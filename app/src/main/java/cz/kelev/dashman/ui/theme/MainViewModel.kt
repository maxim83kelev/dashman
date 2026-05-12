package cz.kelev.dashman.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kelev.dashman.VoiceIntentRouter
import cz.kelev.dashman.services.brain.BrainContract
import cz.kelev.dashman.services.filter.ReminderFilter
import cz.kelev.dashman.services.filter.ReminderFilterMatcher
import cz.kelev.dashman.services.filter.ReminderFilterParser
import cz.kelev.dashman.storage.ReminderEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.activity.result.ActivityResultLauncher
import cz.kelev.dashman.storage.AppPrefs
import cz.kelev.dashman.storage.PremiumChecker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MainViewModel(
    val brain: BrainContract,
    private val appContext: android.content.Context
) : ViewModel() {

    private val activeFilter = MutableStateFlow<ReminderFilter>(ReminderFilter.All)
    val filter: StateFlow<ReminderFilter> = activeFilter.asStateFlow()

    private val isVoiceFilterResultShown = MutableStateFlow(false)
    val voiceFilterResultShown: StateFlow<Boolean> = isVoiceFilterResultShown.asStateFlow()

    private val isDeleteModeActive = MutableStateFlow(false)
    val deleteModeActive: StateFlow<Boolean> = isDeleteModeActive.asStateFlow()

    private val foundCount = MutableStateFlow(0)
    val voiceFoundCount: StateFlow<Int> = foundCount.asStateFlow()

    private var exportLauncher: ActivityResultLauncher<String>? = null
    private var importLauncher: ActivityResultLauncher<Array<String>>? = null

    private val prefs = AppPrefs(appContext)
    private val premiumChecker = PremiumChecker(prefs)

    // Событие: показать диалог "нужен Premium"
    private val _premiumGateEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val premiumGateEvent = _premiumGateEvent.asSharedFlow()

    // Синхронизация счётчика при старте
    init {
        syncVoiceUsageFromServer()
    }

    private fun syncVoiceUsageFromServer() {
        viewModelScope.launch {
            val deviceId = prefs.getDeviceId()
            val count = cz.kelev.dashman.network.DashmanApiClient.getVoiceUsage(deviceId)
            if (count >= 0) { // -1 = офлайн, не трогаем локальный
                prefs.setVoiceDialogCount(count)
            }
        }
    }

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage

    fun setExportLauncher(launcher: ActivityResultLauncher<String>) {
        exportLauncher = launcher
    }

    fun setImportLauncher(launcher: ActivityResultLauncher<Array<String>>) {
        importLauncher = launcher
    }

    fun triggerExport() {
        exportLauncher?.launch("dashman_backup.json")
    }

    fun triggerImport() {
        importLauncher?.launch(arrayOf("application/json"))
    }

    fun onBackupExportSuccess(path: String) {
        val prefs = cz.kelev.dashman.storage.AppPrefs(appContext)
        prefs.setLastBackupTime(System.currentTimeMillis())
        _backupMessage.value = "Резервная копия сохранена:\n$path"
        brain.speak("Резервная копия создана")
    }

    fun onBackupError(msg: String) {
        val spoken = when {
            msg.contains("VERSION_MISMATCH") -> "Файл создан в другой версии приложения и не может быть прочитан"
            msg.contains("прочитать") -> "Не удалось прочитать файл. Возможно, это не файл резервной копии"
            msg.contains("открыть") -> "Не удалось открыть файл для записи"
            else -> "Произошла ошибка при работе с резервной копией"
        }
        _backupMessage.value = msg
        brain.speak(spoken)
    }

    fun restoreFromBackup(reminders: List<ReminderEntity>) {
        viewModelScope.launch {
            val (added, skipped) = brain.insertAll(reminders)
            val uiMsg = "Восстановлено $added, пропущено $skipped дублей"
            val voiceMsg = when {
                added == 0 && skipped > 0 -> "Все напоминания уже есть. Ничего нового не добавлено"
                skipped == 0 -> "Восстановлено $added напоминаний"
                else -> "Восстановлено $added напоминаний, пропущено $skipped дублей"
            }
            _backupMessage.value = uiMsg
            brain.speak(voiceMsg)
        }
    }

    private var clearFilterJob: Job? = null

    val reminders: StateFlow<List<ReminderEntity>> =
        combine(brain.reminders, activeFilter) { source, filter ->
            val filtered = if (filter == ReminderFilter.All) {
                source
            } else {
                source.filter { ReminderFilterMatcher.matches(it, filter) }
            }
            filtered.sortedWith(
                compareBy<ReminderEntity>(
                    { if (it.status == "fired") 0 else 1 },
                    { it.dueAt ?: Long.MAX_VALUE }
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTestReminder() = brain.addTestReminder()

    fun delete(reminder: ReminderEntity) = brain.delete(reminder)

    fun cleanupNow() {
        (brain as? cz.kelev.dashman.services.brain.SimpleBrain)?.cleanupNow()
    }

    fun setCleanupMode(mode: Int) {
        android.util.Log.d("DashmanVM", "setCleanupMode($mode) called")
        (brain as? cz.kelev.dashman.services.brain.SimpleBrain)?.setCleanupMode(mode)
    }

    fun setBriefing(enabled: Boolean, hour: Int, minute: Int) {
        val prefs = cz.kelev.dashman.storage.AppPrefs(appContext)
        prefs.setBriefingEnabled(enabled)
        prefs.setBriefingTime(hour, minute)
        if (enabled) {
            cz.kelev.dashman.services.BriefingScheduler.schedule(appContext, hour, minute)
        } else {
            cz.kelev.dashman.services.BriefingScheduler.cancel(appContext)
        }
        android.util.Log.d("DashmanVM", "setBriefing enabled=$enabled $hour:$minute")
    }

    fun setSpeakOnFire(enabled: Boolean) {
        val prefs = cz.kelev.dashman.storage.AppPrefs(appContext)
        prefs.setSpeakOnFire(enabled)
        android.util.Log.d("DashmanVM", "setSpeakOnFire=$enabled")
    }

    fun addTextReminder(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        brain.addTextReminder(value)
    }

    /**
     * Показать кандидатов на удаление.
     * Фильтр живёт 5 секунд без действий.
     * Если пользователь делает свайп — таймер сбрасывается и даётся ещё 3 секунды.
     */
    fun showDeleteCandidates(candidates: List<ReminderEntity>) {
        val ids = candidates.map { it.id }.toSet()
        activeFilter.value = ReminderFilter.ByIds(ids)
        isVoiceFilterResultShown.value = true
        isDeleteModeActive.value = true
        scheduleFilterClear(delayMs = 7_000L)
    }

    fun showEditCandidates(candidates: List<ReminderEntity>) {
        val ids = candidates.map { it.id }.toSet()
        activeFilter.value = ReminderFilter.ByIds(ids)
        isVoiceFilterResultShown.value = true
        isDeleteModeActive.value = false
        // Таймер не ставим — clearFilter вызовет VoiceEditFlow.clearEditCandidates
        // через коллбэк после завершения диалога
    }

    fun showPostponeCandidate(candidates: List<ReminderEntity>) {
        val ids = candidates.map { it.id }.toSet()
        activeFilter.value = ReminderFilter.ByIds(ids)
        isVoiceFilterResultShown.value = true
        isDeleteModeActive.value = false
        scheduleFilterClear(delayMs = 5_000L)
    }

    fun activateBriefingMode() {
        val todayFilter = cz.kelev.dashman.services.filter.ReminderFilter.Today
        activeFilter.value = todayFilter
        isVoiceFilterResultShown.value = true
        isDeleteModeActive.value = false
        scheduleFilterClear(delayMs = 15_000L)
    }

    /**
     * Вызывается когда пользователь сделал свайп во время режима удаления.
     * Сбрасывает таймер и даёт ещё 3 секунды посмотреть на результат.
     */
    fun onSwipeAction() {
        if (isDeleteModeActive.value || isVoiceFilterResultShown.value) {
            scheduleFilterClear(delayMs = 5_000L)
        }
    }

    /**
     * Явный сброс режима удаления — вызывается из MainActivity
     * когда диалог завершён (да/нет/отмена).
     */
    fun clearDeleteMode() {
        clearFilter()
    }

    fun handleVoiceInput(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return

        when (val intent = VoiceIntentRouter.route(value)) {

            is VoiceIntentRouter.VoiceIntent.Delete -> {
                android.util.Log.w("Dashman", "handleVoiceInput: DELETE reached ViewModel, ignored: '$value'")
            }

            is VoiceIntentRouter.VoiceIntent.Postpone -> {
                android.util.Log.w("Dashman", "handleVoiceInput: POSTPONE reached ViewModel, ignored: '$value'")
                checkVoiceLimit("Перенос напоминаний")
            }

            is VoiceIntentRouter.VoiceIntent.Edit -> {
                android.util.Log.w("Dashman", "handleVoiceInput: EDIT reached ViewModel, ignored: '$value'")
                checkVoiceLimit("Редактирование напоминаний")
            }

            is VoiceIntentRouter.VoiceIntent.Show -> {
                val parsedFilter = ReminderFilterParser.parse(value)
                if (parsedFilter != null) {
                    applyVoiceFilter(parsedFilter)
                } else {
                    android.util.Log.w("Dashman", "handleVoiceInput: SHOW but no filter parsed, falling to CREATE: '$value'")
                    brain.addTextReminder(value)
                }
            }

            is VoiceIntentRouter.VoiceIntent.Create -> {
                brain.addTextReminder(value)
            }

            is VoiceIntentRouter.VoiceIntent.Search -> {
                applyKeywordSearch(intent.keyword)
            }
        }
    }

    fun consumeVoiceDialog() {
        if (premiumChecker.isPremium) return
        prefs.incrementVoiceDialogCount()
        viewModelScope.launch {
            val deviceId = prefs.getDeviceId()
            cz.kelev.dashman.network.DashmanApiClient.incrementVoiceUsage(deviceId)
        }
    }

    fun isVoiceLimitReached(): Boolean {
        if (premiumChecker.isPremium) return false
        return prefs.isVoiceLimitReached()
    }

    private fun checkVoiceLimit(featureName: String) {
        if (!premiumChecker.isPremium && prefs.isVoiceLimitReached()) {
            val used = prefs.getVoiceDialogCount()
            _premiumGateEvent.tryEmit(
                "$featureName доступно только в Premium.\nВ этом месяце использовано $used/20 голосовых диалогов."
            )
        }
    }

    private fun applyVoiceFilter(parsedFilter: ReminderFilter) {
        activeFilter.value = parsedFilter

        val matched = brain.reminders.value
            .filter { ReminderFilterMatcher.matches(it, parsedFilter) }
            .sortedBy { it.dueAt ?: Long.MAX_VALUE }

        foundCount.value = matched.size
        isVoiceFilterResultShown.value = true
        isDeleteModeActive.value = false

        brain.speakFilterResult(parsedFilter, matched.size)

        // Показываем 5 секунд без действий, потом сбрасываем
        scheduleFilterClear(delayMs = 7_000L)
    }

    /**
     * Запускает (или перезапускает) таймер сброса фильтра.
     */
    private fun scheduleFilterClear(delayMs: Long) {
        clearFilterJob?.cancel()
        clearFilterJob = viewModelScope.launch {
            delay(delayMs)
            clearFilter()
        }
    }

    /**
    * Поиск по ключевому слову.
    * Результаты видны на главном экране 15 секунд без действий.
    */
    private fun applyKeywordSearch(keyword: String) {
        viewModelScope.launch {
            val found = brain.searchByKeyword(keyword)

            val ids = found.map { it.id }.toSet()
            activeFilter.value = if (ids.isNotEmpty()) ReminderFilter.ByIds(ids) else ReminderFilter.All
            foundCount.value = found.size
            isVoiceFilterResultShown.value = true
            isDeleteModeActive.value = false

            brain.speak(
                if (found.isEmpty()) cz.kelev.dashman.GreetingPhrases.randomSearchNotFound()
                else cz.kelev.dashman.GreetingPhrases.randomSearchFound()
            )

            scheduleFilterClear(delayMs = 15_000L)
        }
    }

    fun isFilterCommand(text: String): Boolean {
        return VoiceIntentRouter.route(text.trim()) is VoiceIntentRouter.VoiceIntent.Show
    }

    fun clearFilter() {
        clearFilterJob?.cancel()
        clearFilterJob = null
        activeFilter.value = ReminderFilter.All
        foundCount.value = 0
        isVoiceFilterResultShown.value = false
        isDeleteModeActive.value = false
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    override fun onCleared() {
        clearFilterJob?.cancel()
        clearFilterJob = null
        exportLauncher = null
        importLauncher = null
        super.onCleared()
    }
}

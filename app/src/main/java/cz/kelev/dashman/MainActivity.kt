package cz.kelev.dashman

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.viewModels
import cz.kelev.dashman.ui.theme.MainViewModel
import java.io.Closeable
import cz.kelev.dashman.services.AsrController
import cz.kelev.dashman.services.HotwordServiceStarter
import cz.kelev.dashman.services.voice.postpone.VoicePostponeFlow
import cz.kelev.dashman.services.voice.delete.VoiceDeleteFlow
import cz.kelev.dashman.services.voice.edit.VoiceEditFlow
import cz.kelev.dashman.DashmanLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import cz.kelev.dashman.storage.BackupManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import cz.kelev.dashman.services.PremiumExpiryNotifier

class MainActivity : ComponentActivity() {

    private var asr: AsrController? = null
    private lateinit var ttsManager: TtsManager
    private lateinit var asrFlow: AsrFlowController
    private lateinit var hotwordController: HotwordController
    private lateinit var hotwordServiceStarter: HotwordServiceStarter
    private val hotwordEnabledState = mutableStateOf(false)
    private val appGraph by lazy { AppGraph(this) }

    private lateinit var voicePostponeFlow: VoicePostponeFlow
    private lateinit var voiceEditFlow: VoiceEditFlow
    private lateinit var voiceDeleteFlow: VoiceDeleteFlow
    private val deleteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var briefingHandled = false
    private var lastHandledReminderId = -1L
    private val premiumGateMessageState = mutableStateOf<String?>(null)

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            DashmanLogger.d("DashmanPerm", "POST_NOTIFICATIONS result: granted=$granted")
            if (::hotwordController.isInitialized) {
                hotwordController.onNotifPermissionResult(granted)
            }
        }

    private lateinit var permissionCoordinator: PermissionCoordinator

    companion object {
        const val EXTRA_START_ASR = "EXTRA_START_ASR"
    }

    private val dashmanViewModel: MainViewModel by viewModels {
        appGraph.mainViewModelFactory()
    }

    private lateinit var backupManager: BackupManager
    val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            val reminders = dashmanViewModel.reminders.value
            val result = backupManager.exportToUri(it, reminders)
            if (result.isSuccess) {
                dashmanViewModel.onBackupExportSuccess(it.toString())
            } else {
                dashmanViewModel.onBackupError("Ошибка экспорта: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val result = backupManager.importFromUri(it)
            result.onSuccess { backup ->
                dashmanViewModel.restoreFromBackup(backup.reminders)
            }.onFailure { e ->
                dashmanViewModel.onBackupError("Ошибка импорта: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        DashmanLogger.init(this)
        actionBar?.hide()
        title = ""

        backupManager = BackupManager(this)
        dashmanViewModel.setExportLauncher(exportLauncher)
        dashmanViewModel.setImportLauncher(importLauncher)

        permissionCoordinator = PermissionCoordinator(this)
        ttsManager = TtsManager(this)
        hotwordServiceStarter = HotwordServiceStarter(this)

        PremiumExpiryNotifier.schedule(this)

        // Первый запуск — уведомить автора о новой установке
        val prefs = cz.kelev.dashman.storage.AppPrefs(this)
        if (prefs.isFirstLaunch()) {
            prefs.markFirstLaunchDone()
            prefs.getInstallDate() // фиксируем дату установки
            deleteScope.launch {
                cz.kelev.dashman.network.DashmanApiClient.requestAccess(
                    deviceId = prefs.getDeviceId(),
                    telegram = null
                )
            }
        }

        voiceDeleteFlow = VoiceDeleteFlow(
            brain = appGraph.brain,
            matcher = { reminder, query ->
                reminder.text.lowercase().contains(query.lowercase())
            },
            labelProvider = { reminder ->
                reminder.text
            },
            say = { phrase ->
                DashmanLogger.d("Dashman", "VoiceDeleteFlow say: $phrase")
                ttsManager.speakThenDo(phrase) {
                    if (voiceDeleteFlow.isBusy) {
                        runOnUiThread {
                            asr?.start()
                        }
                    }
                }
            },
            showDeleteCandidates = { candidates ->
                DashmanLogger.d("Dashman", "showDeleteCandidates CALLED: ${candidates.size} ids=${candidates.map{it.id}}")
                runOnUiThread {
                    DashmanLogger.d("Dashman", "showDeleteCandidates ON UI THREAD")
                    dashmanViewModel.showDeleteCandidates(candidates)
                    DashmanLogger.d("Dashman", "showDeleteCandidates DONE filter=${dashmanViewModel.filter.value}")
                }
            }
        )

        voicePostponeFlow = VoicePostponeFlow(
            brain = appGraph.brain,
            scope = deleteScope,
            matcher = { reminder, query ->
                reminder.text.lowercase().contains(query.lowercase())
            },
            labelProvider = { reminder ->
                reminder.text
            },
            updateDueAt = { id, dueAt ->
                appGraph.repo.updateDueAtAndActivate(id, dueAt)
                appGraph.engine.scheduleReminder(id, dueAt)
            },
            say = { phrase ->
                DashmanLogger.d("Dashman", "VoicePostponeFlow say: $phrase")
                ttsManager.speakThenDo(phrase) {
                    if (voicePostponeFlow.isBusy) {
                        runOnUiThread { asr?.start() }
                    }
                }
            },
            showCandidate = { candidates ->
                runOnUiThread {
                    dashmanViewModel.showPostponeCandidate(candidates)
                }
            }
        )

        voiceEditFlow = VoiceEditFlow(
            brain = appGraph.brain,
            scope = deleteScope,
            matcher = { reminder, query ->
                reminder.text.lowercase().contains(query.lowercase())
            },
            labelProvider = { reminder ->
                reminder.text
            },
            updateText = { id, text ->
                appGraph.repo.updateText(id, text)
            },
            updateDueAt = { id, dueAt ->
                appGraph.repo.updateDueAtAndActivate(id, dueAt)
                appGraph.engine.scheduleReminder(id, dueAt)
            },
            showEditCandidates = { candidates ->
                runOnUiThread {
                    dashmanViewModel.showEditCandidates(candidates)
                }
            },
            clearEditCandidates = {
                runOnUiThread {
                    dashmanViewModel.clearFilter()
                }
            },
            say = { phrase ->
                DashmanLogger.d("Dashman", "VoiceEditFlow say: $phrase")
                ttsManager.speakThenDo(phrase) {
                    if (voiceEditFlow.isBusy) {
                        runOnUiThread { asr?.start() }
                    }
                }
            }
        )

        // Прогреваем reminders чтобы StateFlow успел загрузить данные из базы
        deleteScope.launch {
            appGraph.brain.reminders.collect { }
        }

        val requestAudioPermission = registerRecordAudioPermissionLauncher { granted ->
            hotwordController.onAudioPermissionResult(granted)
            asrFlow.onAudioPermissionResult(granted)
            DashmanLogger.d("DashmanPerm", "RECORD_AUDIO result: granted=$granted")
        }

        if (!hasRecordAudioPermission()) {
            DashmanLogger.d("DashmanPerm", "Requesting RECORD_AUDIO at startup")
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            DashmanLogger.d("DashmanPerm", "RECORD_AUDIO already granted")
        }

        hotwordController = HotwordController(
            activity = this,
            requestAudioPermission = requestAudioPermission,
            requestNotifPermission = requestNotifPermission,
            hotwordEnabledState = hotwordEnabledState,
            startHotwordService = { hotwordServiceStarter.start() },
            stopHotwordService = { hotwordServiceStarter.stop() }
        )

        asrFlow = AsrFlowController(
            requestAudioPermission = requestAudioPermission,
            hasAudioPermission = { this.hasRecordAudioPermission() },
            startAsr = { asr?.start() }
        )

        asr = AsrController(
            context = this@MainActivity,
            onListeningChanged = { },
            onResult = { text ->
                if (!permissionCoordinator.hasExactAlarmPermission()) {
                    DashmanLogger.d("DashmanPerm", "Exact alarm permission missing, opening settings")
                    permissionCoordinator.openExactAlarmSettings()
                } else {
                    val isEditEntry = !voiceEditFlow.isBusy &&
                        VoiceEditFlow.EDIT_HOT_WORDS.any {
                            text.lowercase().trim().startsWith(it)
                        }
                    val isPostponeEntry = !voicePostponeFlow.isBusy &&
                        VoicePostponeFlow.POSTPONE_WORDS.any {
                            text.lowercase().trim().startsWith(it)
                        }

                    if ((isEditEntry || isPostponeEntry) &&
                        dashmanViewModel.isVoiceLimitReached()) {
                        val used = cz.kelev.dashman.storage.AppPrefs(applicationContext)
                            .getVoiceDialogCount()
                        premiumGateMessageState.value =
                            "Голосовые диалоги доступны только в Premium.\n" +
                            "В этом месяце использовано $used/20."
                        DashmanLogger.d("Dashman", "Voice dialog blocked: limit reached")
                    } else {
                        if (isEditEntry || isPostponeEntry) {
                            dashmanViewModel.consumeVoiceDialog()
                        }
                        val handled =
                            voiceEditFlow.handle(text) ||
                            voicePostponeFlow.handle(text) ||
                            voiceDeleteFlow.handle(text)

                        DashmanLogger.d("Dashman", "onResult text='$text' handled=$handled " +
                            "postponeBusy=${voicePostponeFlow.isBusy} " +
                            "deleteBusy=${voiceDeleteFlow.isBusy}")

                        if (!handled) {
                            dashmanViewModel.handleVoiceInput(text)
                        }
                    }
                }
            },
            onError = { }
        )

        asrFlow.onNewIntent(intent)

        setContent {
            AppRoot(
                activity = this@MainActivity,
                vm = dashmanViewModel,
                ttsManager = ttsManager,
                hotwordEnabledState = hotwordEnabledState,
                onHotwordToggle = { enabled -> hotwordController.onToggle(enabled) },
                onMicClick = { asrFlow.onMicClick() },
                onTermsAccepted = { permissionCoordinator.runFirstLaunchFlowIfNeeded() },
                premiumGateMessage = premiumGateMessageState,
                onPremiumGateDismiss = { premiumGateMessageState.value = null }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Брифинг — только один раз за жизнь Activity
        if (!briefingHandled) {
            briefingHandled = true
            handleBriefingIntent(intent)
        }
        // Напоминание — каждый раз при новом интенте
        handleReminderIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        asrFlow.onNewIntent(intent)
        handleBriefingIntent(intent)
    }

    private fun handleBriefingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(
                cz.kelev.dashman.services.BriefingReceiver.EXTRA_FROM_BRIEFING, false
            ) == true) {
            DashmanLogger.d("DashmanBriefing", "Opened from briefing notification")
            dashmanViewModel.activateBriefingMode()
            ttsManager.speakNow(GreetingPhrases.randomBriefing())
        }
    }

    private fun handleReminderIntent(intent: Intent?) {
        val reminderId = intent?.getLongExtra(
            cz.kelev.dashman.services.ReminderReceiver.EXTRA_REMINDER_ID, -1L
        ) ?: -1L
        if (reminderId <= 0L) return
        if (reminderId == lastHandledReminderId) return
        lastHandledReminderId = reminderId

        val prefs = cz.kelev.dashman.storage.AppPrefs(applicationContext)
        if (!prefs.getSpeakOnFire()) return
        if (!appGraph.premiumChecker.isPremium) return  // speak-on-fire только для Premium

        deleteScope.launch {
            try {
                val reminder = appGraph.repo.getById(reminderId) ?: return@launch
                val text = reminder.text
                DashmanLogger.d("DashmanReminder", "Speaking fired reminder id=$reminderId text=$text")
                runOnUiThread {
                    ttsManager.speakNow("Напоминание: $text")
                }
            } catch (e: Exception) {
                DashmanLogger.e("DashmanReminder", "Failed to speak reminder", e)
            }
        }
    }

    override fun onDestroy() {
        asr?.destroy()
        deleteScope.cancel()

        try {
            (appGraph.brain as? Closeable)?.close()
        } catch (_: Throwable) {}

        try {
            ttsManager.shutdown()
        } catch (_: Throwable) {}

        super.onDestroy()
    }
}
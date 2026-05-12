package cz.kelev.dashman.services.voice.edit

import cz.kelev.dashman.services.brain.BrainContract
import cz.kelev.dashman.services.nlp.TimeAndRepeatParser
import cz.kelev.dashman.storage.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

class VoiceEditFlow(
    private val brain: BrainContract,
    private val scope: CoroutineScope,
    private val matcher: (ReminderEntity, String) -> Boolean,
    private val labelProvider: (ReminderEntity) -> String,
    private val updateText: suspend (id: Long, text: String) -> Unit,
    private val updateDueAt: suspend (id: Long, dueAt: Long) -> Unit,
    private val showEditCandidates: (List<ReminderEntity>) -> Unit = {},
    private val clearEditCandidates: () -> Unit = {},
    private val say: (String) -> Unit = {}
) {

    // ─── Состояние диалога ────────────────────────────────────────────────────

    private enum class State {
        IDLE,
        WAITING_WHAT,       // ждём название напоминания
        WAITING_NEW_TEXT,   // ждём новый текст
        WAITING_TIME_CHOICE, // «время оставляем или меняем?»
        WAITING_WHEN        // ждём новое время
    }

    private var state: State = State.IDLE
    private var pendingReminder: ReminderEntity? = null
    private var highlightClearJob: kotlinx.coroutines.Job? = null

    val isBusy: Boolean
        get() = state != State.IDLE

    fun reset() {
        state = State.IDLE
        pendingReminder = null
        // подсветка не сбрасывается здесь — она живёт ещё несколько секунд после завершения
    }

    private fun scheduleHighlightClear(delayMs: Long = 4_000L) {
        highlightClearJob?.cancel()
        highlightClearJob = scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            kotlinx.coroutines.delay(delayMs)
            clearEditCandidates()
        }
    }

    // ─── Точка входа ──────────────────────────────────────────────────────────

    /**
     * Возвращает true если фраза обработана этим flow (не передавать дальше).
     * Возвращает false если это не команда редактирования.
     */
    fun handle(rawText: String): Boolean {
        val text = normalize(rawText)
        if (text.isBlank()) return false

        // Отмена в любой момент диалога
        if (state != State.IDLE && isCancelPhrase(text)) {
            reset()
            highlightClearJob?.cancel()
            clearEditCandidates()
            say(EditPhrases.cancelled())
            return true
        }

        return when (state) {
            State.IDLE            -> handleIdle(text)
            State.WAITING_WHAT   -> handleWaitingWhat(text)
            State.WAITING_NEW_TEXT -> handleWaitingNewText(rawText.trim()) // raw: сохраняем регистр
            State.WAITING_TIME_CHOICE -> handleWaitingTimeChoice(text)
            State.WAITING_WHEN   -> handleWaitingWhen(text)
        }
    }

    // ─── Шаг 1: начало — команда «измени» ────────────────────────────────────

    private fun handleIdle(text: String): Boolean {
        if (!looksLikeEditCommand(text)) return false

        val extracted = extractQuery(text)

        return if (extracted.isBlank()) {
            state = State.WAITING_WHAT
            say(EditPhrases.askWhat())
            true
        } else {
            tryFindReminder(extracted)
            true
        }
    }

    // ─── Шаг 2: ждём название напоминания ────────────────────────────────────

    private fun handleWaitingWhat(text: String): Boolean {
        val query = cleanupQuery(text)
        if (query.isBlank()) {
            say(EditPhrases.askWhatAgain())
            return true
        }
        tryFindReminder(query)
        return true
    }

    // ─── Шаг 3: ждём новый текст ─────────────────────────────────────────────

    private fun handleWaitingNewText(rawText: String): Boolean {
        val reminder = pendingReminder ?: run {
            reset()
            say(EditPhrases.internalError())
            return true
        }

        // Чистим только пустоту, регистр сохраняем
        val newText = rawText.trim()
        if (newText.isBlank()) {
            say(EditPhrases.askNewTextAgain())
            return true
        }

        scope.launch(Dispatchers.IO) {
            updateText(reminder.id, newText)
        }

        // Обновляем локальную копию для следующего шага
        pendingReminder = reminder.copy(text = newText)

        state = State.WAITING_TIME_CHOICE
        say(EditPhrases.askChangeTime())
        return true
    }

    // ─── Шаг 4: «время меняем или оставляем?» ────────────────────────────────
    private fun handleWaitingTimeChoice(text: String): Boolean {
        return when {
            isKeepTimePhrase(text) -> {
                reset()
                scheduleHighlightClear()
                say(EditPhrases.doneText())
                true
            }
            isChangeTimePhrase(text) -> {
                // Пользователь сказал «да/меняем» без конкретного времени
                state = State.WAITING_WHEN
                say(EditPhrases.askWhen())
                true
            }
            else -> {
                // Может быть сразу сказал время? Пробуем распарсить
                val parsed = TimeAndRepeatParser.parse(text)
                if (parsed.whenDt != null) {
                    applyNewTime(parsed.whenDt)
                } else {
                    // Не понял — переспрашиваем
                    say(EditPhrases.askChangeTime())
                }
                true
            }
        }
    }

    // ─── Шаг 5: ждём новое время ─────────────────────────────────────────────

    private fun handleWaitingWhen(text: String): Boolean {
        val parsed = TimeAndRepeatParser.parse(text)

        return if (parsed.whenDt != null) {
            applyNewTime(parsed.whenDt)
            true
        } else {
            // Если сказали просто «да» или пустышку — просим уточнить
            if (isPositivePhrase(text) || text.isBlank()) {
                say(EditPhrases.clarifyWhen())
            } else {
                say(EditPhrases.askWhenAgain())
            }
            true
        }
    }

    // ─── Применить новое время ────────────────────────────────────────────────

    private fun applyNewTime(whenDt: java.time.LocalDateTime) {
        val reminder = pendingReminder ?: run {
            reset()
            say(EditPhrases.internalError())
            return
        }

        val newDueAt = whenDt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        scope.launch(Dispatchers.IO) {
            updateDueAt(reminder.id, newDueAt)
        }

        reset()
        scheduleHighlightClear()
        say(EditPhrases.doneTime())
    }

    // ─── Поиск совпадения ─────────────────────────────────────────────────────

    private fun tryFindReminder(query: String) {
        val current = brain.reminders.value
        val matches = current.filter { matcher(it, query) }

        when {
            matches.isEmpty() -> {
                state = State.WAITING_WHAT
                say(EditPhrases.notFound())
            }

            matches.size == 1 -> {
                pendingReminder = matches.first()
                state = State.WAITING_NEW_TEXT
                showEditCandidates(matches)
                say("${EditPhrases.found(labelProvider(matches.first()))} ${EditPhrases.askNewText()}")
            }

            else -> {
                pendingReminder = matches.first()
                state = State.WAITING_NEW_TEXT
                showEditCandidates(matches)
                say("${EditPhrases.foundMultiple(matches.size, labelProvider(matches.first()))} ${EditPhrases.askNewText()}")
            }
        }
    }

    // ─── Утилиты ──────────────────────────────────────────────────────────────

    private fun looksLikeEditCommand(text: String): Boolean =
        EDIT_WORDS.any { text == it || text.startsWith("$it ") }

    private fun extractQuery(text: String): String {
        var result = text
        EDIT_WORDS.forEach { word ->
            result = result.replace(Regex("""\b${Regex.escape(word)}\b"""), " ")
        }
        return cleanupQuery(result)
    }

    private fun cleanupQuery(text: String): String {
        var result = normalize(text)
        result = result.replace(
            Regex("""\b(ну|пожалуйста|дашман|эй|слушай|мне|напоминание|напоминания|задачу|задачи|про)\b"""),
            ""
        )
        return result.replace(Regex("""\s+"""), " ").trim()
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    private fun isCancelPhrase(text: String): Boolean =
        normalize(text) in CANCEL_WORDS

    private fun isKeepTimePhrase(text: String): Boolean =
        KEEP_TIME_WORDS.any { text.contains(it) }

    private fun isChangeTimePhrase(text: String): Boolean =
        CHANGE_TIME_WORDS.any { text == it || text.startsWith("$it ") || text.contains(it) }

    private fun isPositivePhrase(text: String): Boolean =
        text in setOf("да", "ага", "угу", "конечно", "давай")

    // ─── Константы ────────────────────────────────────────────────────────────

    companion object {
        val EDIT_HOT_WORDS = setOf(
            "измени", "изменить", "поменяй", "поменять",
            "переименуй", "переименовать", "редактируй", "отредактируй"
        )
    }

    private val EDIT_WORDS = EDIT_HOT_WORDS

    private val CANCEL_WORDS = setOf(
        "отмена", "отменить", "отмени", "стоп", "не надо",
        "хватит", "выход", "назад", "забудь", "забыть"
    )

    // «оставляем» — пользователь хочет сохранить текущее время
    private val KEEP_TIME_WORDS = setOf(
        "оставляем", "оставить", "оставь", "нет", "не надо",
        "не меняем", "не менять", "так оставь", "оставь как есть", "без изменений"
    )

    // «меняем» — пользователь хочет изменить время
    private val CHANGE_TIME_WORDS = setOf(
        "меняем", "менять", "изменить", "да", "ага", "угу",
        "конечно", "давай", "изменяем", "поменять", "поменяем"
    )
}

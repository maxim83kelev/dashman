package cz.kelev.dashman.services.voice.postpone

import cz.kelev.dashman.services.brain.BrainContract
import cz.kelev.dashman.services.nlp.TimeAndRepeatParser
import cz.kelev.dashman.storage.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

class VoicePostponeFlow(
    private val brain: BrainContract,
    private val scope: CoroutineScope,
    private val matcher: (ReminderEntity, String) -> Boolean,
    private val labelProvider: (ReminderEntity) -> String,
    private val updateDueAt: suspend (id: Long, dueAt: Long) -> Unit,
    private val say: (String) -> Unit = {},
    private val showCandidate: (List<ReminderEntity>) -> Unit = {}
) {

    // ─── Состояние диалога ────────────────────────────────────────────────────

    private enum class State { IDLE, WAITING_WHAT, WAITING_WHEN }

    private var state: State = State.IDLE
    private var pendingReminder: ReminderEntity? = null

    val isBusy: Boolean
        get() = state != State.IDLE

    fun reset() {
        state = State.IDLE
        pendingReminder = null
    }

    // ─── Точка входа ──────────────────────────────────────────────────────────

    /**
     * Возвращает true если фраза обработана этим flow (не передавать дальше).
     * Возвращает false если это не команда переноса.
     */
    fun handle(rawText: String): Boolean {
        val text = normalize(rawText)
        if (text.isBlank()) return false

        // Отмена в любой момент диалога
        if (state != State.IDLE && isCancelPhrase(text)) {
            reset()
            say(PostponePhrases.cancelled())
            return true
        }

        return when (state) {
            State.IDLE         -> handleIdle(text)
            State.WAITING_WHAT -> handleWaitingWhat(text)
            State.WAITING_WHEN -> handleWaitingWhen(text)
        }
    }

    // ─── Шаг 1: начало — команда «перенеси» ──────────────────────────────────

    private fun handleIdle(text: String): Boolean {
        if (!looksLikePostponeCommand(text)) return false

        val extracted = extractQuery(text)

        return if (extracted.isBlank()) {
            state = State.WAITING_WHAT
            say(PostponePhrases.askWhat())
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
            say(PostponePhrases.askWhatAgain())
            return true
        }
        tryFindReminder(query)
        return true
    }

    // ─── Шаг 3: ждём новое время ─────────────────────────────────────────────

    private fun handleWaitingWhen(text: String): Boolean {
        val reminder = pendingReminder ?: run {
            reset()
            say(PostponePhrases.internalError())
            return true
        }

        val parsed = TimeAndRepeatParser.parse(text)

        if (parsed.whenDt == null) {
            say(PostponePhrases.askWhenAgain())
            return true
        }

        val newDueAt = parsed.whenDt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        scope.launch(Dispatchers.IO) {
            updateDueAt(reminder.id, newDueAt)
        }

        reset()
        say(PostponePhrases.done())
        return true
    }

    // ─── Поиск совпадения ─────────────────────────────────────────────────────

    private fun tryFindReminder(query: String) {
        val current = brain.reminders.value
        val matches = current.filter { matcher(it, query) }

        when {
            matches.isEmpty() -> {
                state = State.WAITING_WHAT
                say(PostponePhrases.notFound())
            }

            matches.size == 1 -> {
                pendingReminder = matches.first()
                state = State.WAITING_WHEN
                showCandidate(matches)
                say("${PostponePhrases.found(labelProvider(matches.first()))} ${PostponePhrases.askWhen()}")
            }

            else -> {
                pendingReminder = matches.first()
                state = State.WAITING_WHEN
                showCandidate(matches)
                say("${PostponePhrases.foundMultiple(matches.size, labelProvider(matches.first()))} ${PostponePhrases.askWhen()}")
            }
        }
    }

    // ─── Утилиты ──────────────────────────────────────────────────────────────

    private fun looksLikePostponeCommand(text: String): Boolean =
        POSTPONE_WORDS.any { text == it || text.startsWith("$it ") }

    private fun extractQuery(text: String): String {
        var result = text
        POSTPONE_WORDS.forEach { word ->
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

    // ─── Константы ────────────────────────────────────────────────────────────

    companion object {

        val POSTPONE_WORDS = setOf(
            "перенеси", "перенесть", "перенести", "перенес",
            "сдвинь", "сдвинуть", "отложи", "отложить"
        )

        val CANCEL_WORDS = setOf(
            "отмена", "отменить", "отмени", "стоп", "не надо",
            "хватит", "выход", "назад", "забудь", "забыть"
        )
    }
}

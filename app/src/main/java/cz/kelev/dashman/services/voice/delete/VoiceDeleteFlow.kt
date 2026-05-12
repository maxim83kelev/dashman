package cz.kelev.dashman.services.voice.delete

import cz.kelev.dashman.services.brain.BrainContract
import cz.kelev.dashman.storage.ReminderEntity
import cz.kelev.dashman.services.filter.ReminderFilterMatcher
import cz.kelev.dashman.services.filter.ReminderFilterParser
class VoiceDeleteFlow(
    private val brain: BrainContract,
    private val matcher: (ReminderEntity, String) -> Boolean,
    private val labelProvider: (ReminderEntity) -> String,
    private val say: (String) -> Unit = {},
    private val showDeleteCandidates: (List<ReminderEntity>) -> Unit = {}
) {

    private enum class State { IDLE, WAITING_WHAT, WAITING_CONFIRM }

    private var state: State = State.IDLE
    private var pendingReminder: ReminderEntity? = null

    val isBusy: Boolean
        get() = state != State.IDLE

    fun reset() {
        state = State.IDLE
        pendingReminder = null
    }

    /**
     * Возвращает true, если фраза была обработана этим flow.
     * Возвращает false, если это вообще не команда удаления.
     */
    fun handle(rawText: String): Boolean {
        val text = normalize(rawText)
        if (text.isBlank()) return false

        // Отмена в любой момент
        if (state != State.IDLE && isCancelPhrase(text)) {
            reset()
            say("Окей, отменяю.")
            return true
        }

        return when (state) {
            State.IDLE           -> handleIdle(text)
            State.WAITING_WHAT  -> handleDeleteQuery(text)
            State.WAITING_CONFIRM -> handleConfirmation(text)
        }
    }

    private fun handleIdle(text: String): Boolean {
        if (!looksLikeDeleteCommand(text)) return false

        val extractedQuery = extractDeleteQuery(text)

        return if (extractedQuery.isBlank()) {
            state = State.WAITING_WHAT
            say(PhrasePool.askWhatToDelete())
            true
        } else {
            handleDeleteQuery(extractedQuery)
        }
    }

    private fun handleDeleteQuery(rawQuery: String): Boolean {
        val query = cleanupQuery(rawQuery)
        if (query.isBlank()) {
            state = State.WAITING_WHAT
            say(PhrasePool.askWhatToDelete())
            return true
        }

        val current = brain.reminders.value
        val filter = parseDeleteFilter(query)
        val matches = if (filter != null) {
            current.filter { ReminderFilterMatcher.matches(it, filter) }
        } else {
            current.filter { matcher(it, query) }
        }

        when {
            matches.isEmpty() -> {
                state = State.WAITING_WHAT
                say(PhrasePool.nothingFound())
            }

            matches.size == 1 -> {
                pendingReminder = matches.first()
                state = State.WAITING_CONFIRM
                showDeleteCandidates(matches)
                say("Нашёл: ${labelProvider(matches.first())}. ${PhrasePool.confirmDelete()}")
            }

            else -> {
                reset()
                showDeleteCandidates(matches)
                say("${PhrasePool.found(matches.size)} Удали нужное свайпом.")
            }
        }
        return true
    }

    private fun handleConfirmation(text: String): Boolean {
        return when {
            isYes(text) -> {
                val reminder = pendingReminder
                reset()
                if (reminder != null) {
                    val deleted = ReminderDeletion.deleteById(
                        current = brain.reminders.value,
                        id = reminder.id,
                        deleter = brain::delete
                    )
                    if (deleted) say(PhrasePool.deleted())
                    else say("Не получилось удалить. Напоминание не найдено.")
                } else {
                    say("Не получилось удалить. Напоминание не найдено.")
                }
                true
            }

            isNo(text) -> {
                reset()
                say("Окей, не удаляю.")
                true
            }

            else -> {
                say("Скажи да или нет.")
                true
            }
        }
    }

    private fun looksLikeDeleteCommand(text: String): Boolean {
        return Regex("""\bудал\w*\b""").containsMatchIn(text)
    }

    private fun extractDeleteQuery(text: String): String {
        var result = text

        DELETE_WORDS.forEach { word ->
            result = result.replace(Regex("""\b$word\b"""), " ")
        }

        return cleanupQuery(result)
    }

    private fun parseDeleteFilter(query: String) =
        ReminderFilterParser.parse(
            when {
                query == "сегодня" || query == "на сегодня" -> "что у меня сегодня"
                query == "завтра" || query == "на завтра" -> "что у меня завтра"
                query == "послезавтра" || query == "на послезавтра" -> "что у меня послезавтра"
                query == "на этой неделе" || query == "на неделе" || query == "на эту неделю" ->
                    "что на этой неделе"
                query == "на выходных" || query == "на этих выходных" ->
                    "что на выходных"
                query == "в этом месяце" || query == "на этот месяц" ->
                    "что в этом месяце"
                else -> "что у меня $query"
            }
        )

    private fun cleanupQuery(text: String): String {
        var result = normalize(text)
        result = result.replace(
            Regex("""\b(ну|пожалуйста|дашман|эй|слушай|мне|напоминание|напоминания|задачу|задачи|про)\b"""),
            ""
        )
        result = result.replace(Regex("""\s+"""), " ").trim()
        return result
    }

    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isYes(text: String): Boolean {
        val t = normalize(text)
        return t in YES_WORDS
    }

    private fun isNo(text: String): Boolean {
        val t = normalize(text)
        return t in NO_WORDS
    }

    private fun isCancelPhrase(text: String): Boolean =
        normalize(text) in NO_WORDS

    private companion object {
        val DELETE_WORDS = setOf(
            "удали",
            "удалить",
            "удалил",
            "удалитька"
        )

        val YES_WORDS = setOf(
            "да",
            "ага",
            "угу",
            "подтверждаю",
            "подтвердить",
            "удаляй",
            "удали",
            "давай"
        )

        val NO_WORDS = setOf(
            "нет",
            "не",
            "отмена",
            "отменить",
            "не надо"
        )
    }
}

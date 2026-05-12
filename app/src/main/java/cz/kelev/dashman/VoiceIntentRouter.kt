package cz.kelev.dashman

import cz.kelev.dashman.services.voice.edit.VoiceEditFlow

object VoiceIntentRouter {

    sealed class VoiceIntent {
        object Postpone                        : VoiceIntent()
        object Delete                          : VoiceIntent()
        object Edit                            : VoiceIntent()
        object Show                            : VoiceIntent()
        object Create                          : VoiceIntent()
        data class Search(val keyword: String) : VoiceIntent()
    }

    fun route(raw: String): VoiceIntent {
        val t = normalize(raw)

        if (startsWithAny(t, POSTPONE_HOT_WORDS)) return VoiceIntent.Postpone
        if (startsWithAny(t, DELETE_HOT_WORDS))   return VoiceIntent.Delete
        if (startsWithAny(t, EDIT_HOT_WORDS))     return VoiceIntent.Edit
        if (startsWithAny(t, SHOW_HOT_WORDS))     return VoiceIntent.Show
        extractSearchKeyword(t)?.let              { return VoiceIntent.Search(it) }

        return VoiceIntent.Create
    }

    // ─── Проверка ──────────────────────────────────────────────────────────────

    private fun startsWithAny(t: String, words: Set<String>): Boolean =
        words.any { t == it || t.startsWith("$it ") }

    // ─── Нормализация ──────────────────────────────────────────────────────────

    private fun normalize(text: String): String =
        text.lowercase()
            .replace('ё', 'е')
            .replace(Regex("\\s+"), " ")
            .trim()

    // ─── Горячие слова — DELETE ────────────────────────────────────────────────

    private val DELETE_HOT_WORDS = setOf(
        "удали", "удалить", "убери", "убрать", "сотри", "стереть"
    )

    // ─── Горячие слова — EDIT ─────────────────────────────────────────────────

    private val EDIT_HOT_WORDS = setOf(
        "измени", "изменить", "поменяй", "поменять",
        "переименуй", "переименовать", "редактируй", "отредактируй"
    )

    // ─── Горячие слова — SHOW ─────────────────────────────────────────────────

    private val SHOW_HOT_WORDS = setOf(
        "что", "покажи", "есть ли"
    )

    // ─── Горячие слова — SEARCH ───────────────────────────────────────────────

    private val SEARCH_HOT_WORDS = setOf(
        "найди", "найти", "ищи", "искать", "поиск"
    )

    private val SEARCH_STRIP_PREFIXES = listOf(
        "напоминание про ", "напоминания про ", "напоминание о ", "напоминания о ",
        "напоминание об ", "напоминания об ", "напоминание ",  "напоминания ",
        "про ", "о ", "об ", "с ", "со словом ", "где "
    )

    private val POSTPONE_HOT_WORDS = setOf(
        "перенеси", "перенести", "перенесть", "перенес",
        "сдвинь", "сдвинуть", "отложи", "отложить"
    )

    private fun extractSearchKeyword(t: String): String? {
        val hotWord = SEARCH_HOT_WORDS.firstOrNull { t == it || t.startsWith("$it ") }
            ?: return null

        var rest = t.removePrefix(hotWord).trim()

        for (prefix in SEARCH_STRIP_PREFIXES) {
            if (rest.startsWith(prefix)) {
                rest = rest.removePrefix(prefix).trim()
                break
            }
        }

        return rest.ifBlank { null }
    }
}
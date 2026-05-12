package cz.kelev.dashman.services.filter

import java.time.DayOfWeek
import java.time.LocalDate

object ReminderFilterParser {

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReminderFilter? {
        val t = normalize(text)

        when (t) {
            "покажи все",
            "покажи всё",
            "покажи все напоминания",
            "все напоминания",
            "сбрось фильтр" -> return ReminderFilter.All
        }

        val base = parseBaseFilter(t, today)
        val partOfDay = parsePartOfDay(t)

        // Если есть часть суток без базовой даты — привязываем к сегодня
        // «что вечером» = «что сегодня вечером», а не все вечера всех дат
        if (base != null && partOfDay != null) {
            return ReminderFilter.Combined(base, partOfDay)
        }

        if (partOfDay != null) {
            return ReminderFilter.Combined(ReminderFilter.Today, partOfDay)
        }

        return base
    }

    private fun parseBaseFilter(t: String, today: LocalDate): ReminderFilter? {

        // ── Сегодня ────────────────────────────────────────────────────────
        if (t == "что сегодня" ||
            t == "что на сегодня" ||
            t == "что у меня сегодня" ||
            t == "что у меня на сегодня" ||
            t.startsWith("покажи сегодня") ||
            t.startsWith("что сегодня ") ||
            t.startsWith("что на сегодня ") ||
            t.contains("сегодня утром") ||
            t.contains("сегодня вечером") ||
            t.contains("сегодня днем") ||
            t.contains("сегодня ночью")) {
            return ReminderFilter.Today
        }

        // ── Завтра ─────────────────────────────────────────────────────────
        if ((t == "что завтра" ||
            t == "что на завтра" ||
            t == "что у меня завтра" ||
            t == "что у меня на завтра" ||
            t.startsWith("покажи завтра") ||
            t.startsWith("что завтра ") ||
            t.startsWith("что на завтра ") ||
            (t.contains("завтра утром") && !t.contains("послезавтра")) ||
            (t.contains("завтра вечером") && !t.contains("послезавтра")) ||
            (t.contains("завтра днем") && !t.contains("послезавтра")) ||
            (t.contains("завтра ночью") && !t.contains("послезавтра")))) {
            return ReminderFilter.Tomorrow
        }

        // ── Послезавтра ────────────────────────────────────────────────────
        if (t == "что послезавтра" ||
            t == "что на послезавтра" ||
            t == "что у меня послезавтра" ||
            t.startsWith("покажи послезавтра") ||
            t.startsWith("что послезавтра ") ||
            t.startsWith("что на послезавтра ") ||
            t.contains("послезавтра утром") ||
            t.contains("послезавтра вечером") ||
            t.contains("послезавтра днем") ||
            t.contains("послезавтра ночью")) {
            return ReminderFilter.DayAfterTomorrow
        }

        // ── Неделя ─────────────────────────────────────────────────────────
        if (t == "что на этой неделе" ||
            t == "что у меня на этой неделе" ||
            t == "что на неделе" ||
            t == "что на эту неделю" ||
            t.startsWith("покажи неделю")) {
            return ReminderFilter.ThisWeek
        }

        // ── Месяц ──────────────────────────────────────────────────────────
        if (t == "что в этом месяце" ||
            t == "что у меня в этом месяце" ||
            t == "что на этот месяц" ||
            t.startsWith("покажи месяц")) {
            return ReminderFilter.ThisMonth
        }

        // ── Выходные ───────────────────────────────────────────────────────
        if (t == "что на выходных" ||
            t == "что на этих выходных" ||
            t == "что у меня на выходных" ||
            t == "что у меня на этих выходных") {
            return ReminderFilter.ThisWeekend
        }

        parseExactDayAndMonth(t, today)?.let { return ReminderFilter.ByExactDate(it) }
        parseInDays(t)?.let { return it }
        parseWeekday(t)?.let { return it }
        parseNearestDayOfMonth(t, today)?.let { return ReminderFilter.ByExactDate(it) }
        parseMonth(t, today)?.let { return it }

        return null
    }

    private fun parseInDays(t: String): ReminderFilter? {
        val regex = Regex("""(?:что(?: у меня)?\s+)?через\s+(\d+)\s+дн(?:я|ей|ь)?""")
        val match = regex.find(t) ?: return null
        val days = match.groupValues[1].toIntOrNull() ?: return null
        if (days < 0) return null
        return ReminderFilter.InDays(days)
    }

    private fun parseWeekday(t: String): ReminderFilter? {
        return when {
            t.contains("понедельник") -> ReminderFilter.ByWeekday(DayOfWeek.MONDAY)
            t.contains("вторник")     -> ReminderFilter.ByWeekday(DayOfWeek.TUESDAY)
            t.contains("среду") || t.contains("среда") -> ReminderFilter.ByWeekday(DayOfWeek.WEDNESDAY)
            t.contains("четверг")     -> ReminderFilter.ByWeekday(DayOfWeek.THURSDAY)
            t.contains("пятницу") || t.contains("пятница") -> ReminderFilter.ByWeekday(DayOfWeek.FRIDAY)
            t.contains("субботу") || t.contains("суббота") -> ReminderFilter.ByWeekday(DayOfWeek.SATURDAY)
            t.contains("воскресенье") -> ReminderFilter.ByWeekday(DayOfWeek.SUNDAY)
            else -> null
        }
    }

    private fun parseNearestDayOfMonth(t: String, today: LocalDate): LocalDate? {
        // "26го", "на 26е", "26 числа", "на 26", "что на 13"
        val regexes = listOf(
            Regex("""(\d{1,2})-?го(?:\s+числа)?"""),
            Regex("""на\s+(\d{1,2})-?е(?:\s+число)?"""),
            Regex("""(\d{1,2})\s+числа"""),
            Regex("""(?:что|покажи)(?:\s+у\s+меня)?\s+на\s+(\d{1,2})(?:\s|$)""")
        )

        var day: Int? = null
        for (regex in regexes) {
            val match = regex.find(t)
            val value = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (value != null && value in 1..31) {
                day = value
                break
            }
        }

        if (day == null) return null

        val thisMonth = runCatching {
            LocalDate.of(today.year, today.month, day)
        }.getOrNull()

        if (thisMonth != null && !thisMonth.isBefore(today)) {
            return thisMonth
        }

        val nextMonthBase = today.plusMonths(1)
        return runCatching {
            LocalDate.of(nextMonthBase.year, nextMonthBase.month, day)
        }.getOrNull()
    }

    /**
     * Парсит «5 апреля», «пятое апреля», «19 марта», «двадцать второго декабря» и т.д.
     * Если дата уже прошла — возвращает следующий год.
     */
    private fun parseExactDayAndMonth(t: String, today: LocalDate): LocalDate? {

        val monthMap = mapOf(
            "январ" to 1, "феврал" to 2, "март" to 3, "апрел" to 4,
            "мая" to 5, "май" to 5, "июн" to 6, "июл" to 7,
            "август" to 8, "сентябр" to 9, "октябр" to 10,
            "ноябр" to 11, "декабр" to 12
        )

        val ordinalMap = mapOf(
            "первого" to 1, "первое" to 1,
            "второго" to 2, "второе" to 2,
            "третьего" to 3, "третье" to 3,
            "четвертого" to 4, "четвертое" to 4,
            "пятого" to 5, "пятое" to 5,
            "шестого" to 6, "шестое" to 6,
            "седьмого" to 7, "седьмое" to 7,
            "восьмого" to 8, "восьмое" to 8,
            "девятого" to 9, "девятое" to 9,
            "десятого" to 10, "десятое" to 10,
            "одиннадцатого" to 11, "одиннадцатое" to 11,
            "двенадцатого" to 12, "двенадцатое" to 12,
            "тринадцатого" to 13, "тринадцатое" to 13,
            "четырнадцатого" to 14, "четырнадцатое" to 14,
            "пятнадцатого" to 15, "пятнадцатое" to 15,
            "шестнадцатого" to 16, "шестнадцатое" to 16,
            "семнадцатого" to 17, "семнадцатое" to 17,
            "восемнадцатого" to 18, "восемнадцатое" to 18,
            "девятнадцатого" to 19, "девятнадцатое" to 19,
            "двадцатого" to 20, "двадцатое" to 20,
            "двадцать первого" to 21, "двадцать первое" to 21,
            "двадцать второго" to 22, "двадцать второе" to 22,
            "двадцать третьего" to 23, "двадцать третье" to 23,
            "двадцать четвертого" to 24, "двадцать четвертое" to 24,
            "двадцать пятого" to 25, "двадцать пятое" to 25,
            "двадцать шестого" to 26, "двадцать шестое" to 26,
            "двадцать седьмого" to 27, "двадцать седьмое" to 27,
            "двадцать восьмого" to 28, "двадцать восьмое" to 28,
            "двадцать девятого" to 29, "двадцать девятое" to 29,
            "тридцатого" to 30, "тридцатое" to 30,
            "тридцать первого" to 31, "тридцать первое" to 31
        )

        // Определяем месяц
        val month = monthMap.entries.firstOrNull { t.contains(it.key) }?.value ?: return null

        // Определяем день — сначала прописью (длинные варианты первыми)
        var day: Int? = null
        for ((word, num) in ordinalMap.entries.sortedByDescending { it.key.length }) {
            if (t.contains(word)) { day = num; break }
        }

        // Потом цифрой: "5 апреля", "22 марта"
        if (day == null) {
            val m = Regex("""(\d{1,2})\s+(?:январ|феврал|март|апрел|мая|май|июн|июл|август|сентябр|октябр|ноябр|декабр)""").find(t)
            day = m?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        if (day == null || day !in 1..31) return null

        var date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        if (date.isBefore(today)) {
            date = runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull() ?: return null
        }
        return date
    }

    private fun parseMonth(t: String, today: LocalDate): ReminderFilter? {
        val month = when {
            t.contains("январ")   -> 1
            t.contains("феврал")  -> 2
            t.contains("март")    -> 3
            t.contains("апрел")   -> 4
            t.contains("мая") || t.contains("май") -> 5
            t.contains("июн")     -> 6
            t.contains("июл")     -> 7
            t.contains("август")  -> 8
            t.contains("сентябр") -> 9
            t.contains("октябр")  -> 10
            t.contains("ноябр")   -> 11
            t.contains("декабр")  -> 12
            else -> return null
        }

        val year = if (month >= today.monthValue) today.year else today.year + 1
        return ReminderFilter.ByMonth(month, year)
    }

    private fun parsePartOfDay(t: String): ReminderFilter.PartOfDay? {
        return when {
            t.contains("утром") || t.contains("утра") || t.contains("с утра") -> ReminderFilter.PartOfDay.Morning
            t.contains("днем") || t.contains("днём") || t.contains("дня") -> ReminderFilter.PartOfDay.Afternoon
            t.contains("вечером") || t.contains("вечера") || t.contains("на вечер") -> ReminderFilter.PartOfDay.Evening
            t.contains("ночью") || t.contains("ночи") || t.contains("на ночь") -> ReminderFilter.PartOfDay.Night
            else -> null
        }
    }

    private fun normalize(text: String): String {
        return text
            .trim()
            .lowercase()
            .replace("ё", "е")
            .replace(Regex("\\s+"), " ")
    }
}

package cz.kelev.dashman.services.nlp

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RU_WEEKDAYS = listOf(
    "понедельник","вторник","среда","четверг","пятница","суббота","воскресенье"
)
private val RU_WEEKDAYS_ACC = listOf(
    "понедельник","вторник","среду","четверг","пятницу","субботу","воскресенье"
)

object TimeAndRepeatParser {

    fun parse(raw: String, now: LocalDateTime = LocalDateTime.now()): TimeParseResult {
        val t = norm(raw)
        android.util.Log.d("DashmanParser", "norm result: '$t'")

        // 1) ambiguous relative: "через N" без единицы -> просим единицу
        Regex("\\bчерез\\s+(\\d+)\\b").find(t)?.let { m ->
            val n = m.groupValues[1].toIntOrNull()
            if (n != null) {
                val hasUnit = Regex("\\bчерез\\s+\\d+\\s*(секунд|минут|час|д(ень|ня|ней)|недел|месяц|год)").containsMatchIn(t)
                if (!hasUnit) return TimeParseResult(null, parseRepeat(t), NeedClarification.Unit(n))
            }
        }

        // 2) repeat
        val repeat = parseRepeat(t)

        // 2.1) диапазон повторений
        val repeatRange = parseRepeatRange(t, now)

        // 3) relative time
        val rel = parseRelative(t, now)
        if (rel != null) return TimeParseResult(rel, repeat, null,
            repeatFrom = repeatRange?.repeatFrom,
            repeatUntil = repeatRange?.repeatUntil,
            dayRangeStart = repeatRange?.dayRangeStart,
            dayRangeEnd = repeatRange?.dayRangeEnd)

        // 4) time token (hh:mm, "без четверти восемь", "полвосьмого", "в 6")
        val tm = parseTimeToken(t)
        android.util.Log.d("DashmanParser", "parseTimeToken result: tm=$tm, t contains веч=${t.contains("веч")}")

        // 5) base date (explicit date / weekday / today-tomorrow)
        val baseDate = detectBaseDate(t, now.toLocalDate())

        if (baseDate != null) {
            if (tm != null) {
                val dt = LocalDateTime.of(baseDate, tm)
                // Если явно сказал "сегодня" — не переносим даже если время прошло
                // Если не сказал дату явно — переносим на следующий день
                val fixed = if (dt <= now) dt.plusDays(1) else dt
                return TimeParseResult(fixed, repeat, null,
                    repeatFrom = repeatRange?.repeatFrom,
                    repeatUntil = repeatRange?.repeatUntil,
                    dayRangeStart = repeatRange?.dayRangeStart,
                    dayRangeEnd = repeatRange?.dayRangeEnd)
            }
            // дата есть, времени нет — пробуем время суток
            val partTime = parsePartOfDayTime(t)
            if (partTime != null) {
                val dt = LocalDateTime.of(baseDate, partTime)
                return TimeParseResult(dt, repeat, null,
                    repeatFrom = repeatRange?.repeatFrom,
                    repeatUntil = repeatRange?.repeatUntil,
                    dayRangeStart = repeatRange?.dayRangeStart,
                    dayRangeEnd = repeatRange?.dayRangeEnd)
            }
            // ни времени ни части суток -> просим время
            val dtDefault = LocalDateTime.of(baseDate, LocalTime.of(9, 0))
            return TimeParseResult(dtDefault, repeat, NeedClarification.Time(),
                repeatFrom = repeatRange?.repeatFrom,
                repeatUntil = repeatRange?.repeatUntil,
                dayRangeStart = repeatRange?.dayRangeStart,
                dayRangeEnd = repeatRange?.dayRangeEnd)
        }

        // 6) only time -> today/next day
        // Также ловим только время суток без конкретного времени
        val partTime = parsePartOfDayTime(t)
        val resolvedTime = tm ?: partTime
        if (resolvedTime != null) {
            var dt = LocalDateTime.of(now.toLocalDate(), resolvedTime)
            // Без явной даты — если время прошло, ставим на завтра
            if (!dt.isAfter(now)) dt = dt.plusDays(1)
            return TimeParseResult(dt, repeat, null,
                repeatFrom = repeatRange?.repeatFrom,
                repeatUntil = repeatRange?.repeatUntil,
                dayRangeStart = repeatRange?.dayRangeStart,
                dayRangeEnd = repeatRange?.dayRangeEnd)
        }

        // 7) если есть диапазон дат без времени — первое срабатывание = начало диапазона в 9:00
        if (repeatRange?.repeatFrom != null) {
            val startDt = repeatRange.repeatFrom!!.toLocalDate().atTime(9, 0)
            return TimeParseResult(
                whenDt = startDt,
                repeat = repeat,
                need = null,
                repeatFrom = repeatRange.repeatFrom,
                repeatUntil = repeatRange.repeatUntil,
                dayRangeStart = repeatRange.dayRangeStart,
                dayRangeEnd = repeatRange.dayRangeEnd
            )
        }

        // 8) если есть повтор без времени — первое срабатывание сейчас + 1 минута
        if (repeat != null) {
            return TimeParseResult(now.plusMinutes(1), repeat, null,
                repeatFrom = repeatRange?.repeatFrom,
                repeatUntil = repeatRange?.repeatUntil,
                dayRangeStart = repeatRange?.dayRangeStart,
                dayRangeEnd = repeatRange?.dayRangeEnd)
        }

        // 9) nothing
        return TimeParseResult(null, repeat, NeedClarification.Time())
    }

    private fun norm(text: String): String {
        var t = text.lowercase().replace("ё", "е")
        t = t.replace(Regex("\\b(пожалуйста|дашман|напомни|напоминай|мне|плиз|слышь|ну|короче|типа)\\b"), " ")
        t = t.replace("в половину", "в пол").replace("в половине", "в пол")
        t = t.replace(Regex("\\bпол\\s+([а-я]+)\\b"), "пол$1")
        t = t.replace(Regex("\\s+"), " ").trim()
        android.util.Log.d("DashmanParser", "OUTPUT of norm: '$t'")
        return t
    }

    // ─── parseRepeat ────────────────────────────────────────────────────────
    // Возвращает RepeatRule, который через toStorageString()
    // превращается в строку для RepeatCalculator.next()
    //
    // Таблица соответствия:
    //   RepeatRule.Daily          -> "every_1_days"
    //   RepeatRule.Weekdays       -> "weekdays"
    //   RepeatRule.Weekend        -> "weekends"
    //   RepeatRule.Monthly        -> "monthly"
    //   RepeatRule.Every(n,MIN)   -> "every_N_minutes"
    //   RepeatRule.Every(n,HOUR)  -> "every_N_hours"
    //   RepeatRule.Every(n,DAY)   -> "every_N_days"
    //   RepeatRule.Every(n,WEEK)  -> "every_N_weeks"

    private fun parseRepeat(t: String): RepeatRule? {

        // ── ежемесячно / каждый месяц ──────────────────────────────────────
        if (t.contains("ежемесячно") ||
            Regex("\\bкаждый\\s+месяц\\b").containsMatchIn(t) ||
            Regex("\\bкаждого\\s+месяца\\b").containsMatchIn(t)) {
            return RepeatRule.Monthly
        }

        // ── будние / рабочие дни ───────────────────────────────────────────
        if (t.contains("по будням") ||
            t.contains("каждый будний") ||
            t.contains("в будни") ||
            t.contains("по рабочим")) {
            return RepeatRule.Weekdays
        }
        // ── выходные ───────────────────────────────────────────────────────
        if (t.contains("по выходным") ||
            t.contains("каждые выходные") ||
            t.contains("в выходные")) {
            return RepeatRule.Weekend
        }

        // ── каждый день / ежедневно ────────────────────────────────────────
        if (t.contains("каждый день") ||
            t.contains("ежедневно") ||
            t.contains("каждые сутки") ||
            t.contains("каждое утро") ||
            t.contains("каждый вечер") ||
            t.contains("по утрам") ||
            t.contains("по вечерам")) {
            return RepeatRule.Daily
        }

        // ── еженедельно / каждую неделю ────────────────────────────────────
        if (t.contains("еженедельно") ||
            Regex("\\bкаждую\\s+недел\\w+\\b").containsMatchIn(t) ||
            Regex("\\bкаждую\\s+неделю\\b").containsMatchIn(t)) {
            return RepeatRule.Every(1, RepeatRule.Unit.WEEK)
        }

        // ── каждые N единиц / каждый час / каждую минуту ──────────────────
        // сначала "каждую минуту" / "каждый час" (без числа)
        if (Regex("\\bкаждую\\s+минуту\\b").containsMatchIn(t)) return RepeatRule.Every(1, RepeatRule.Unit.MINUTE)
        if (Regex("\\bкаждый\\s+час\\b").containsMatchIn(t))    return RepeatRule.Every(1, RepeatRule.Unit.HOUR)

        // "каждые N минут/часов/дней/недель"
        val m = Regex("каждые?\\s+(\\d+)\\s*(минут|час|д(ень|ня|ней)|недел)").find(t)
        if (m != null) {
            val n = m.groupValues[1].toIntOrNull() ?: 1
            val unitRaw = m.groupValues[2]
            val unit = when {
                unitRaw.startsWith("мин") -> RepeatRule.Unit.MINUTE
                unitRaw.startsWith("час") -> RepeatRule.Unit.HOUR
                unitRaw.startsWith("нед") -> RepeatRule.Unit.WEEK
                else                      -> RepeatRule.Unit.DAY
            }
            return RepeatRule.Every(n.coerceAtLeast(1), unit)
        }

        return null
    }

    // ─── parseRelative ──────────────────────────────────────────────────────

    private fun parseRelative(t: String, now: LocalDateTime): LocalDateTime? {

        // "через пару часов/дней/недель/месяц"
        Regex("\\bчерез\\s+пару\\s*(час|д(ень|ня|ней)|недел|месяц)\\b").find(t)?.let { m ->
            val u = m.groupValues[1]
            return when {
                u.startsWith("час")   -> now.plusHours(2)
                u.startsWith("нед")   -> now.plusWeeks(2)
                u.startsWith("месяц") -> now.plusDays(60)
                else                  -> now.plusDays(2)
            }
        }
        // "через полчаса"
        if (Regex("\\bчерез\\s+пол\\s*час").containsMatchIn(t) || t.contains("через полчаса")) {
            return now.plusMinutes(30)
        }
        // "через полтора"
        if (Regex("\\bчерез\\s+полтор(а|ы)(\\s*час)?\\b").containsMatchIn(t)) {
            return now.plusMinutes(90)
        }

        // "через 3 часа/10 минут/2 дня..."
        val m = Regex("\\bчерез\\s+(\\d+)\\s*(секунд|секунду|секунды|минут|минуту|минуты|час|часа|часов|д(ень|ня|ней)|сутки|недел|неделю|недели|месяц|месяца|месяцев|год|года|лет)\\b").find(t)
            if (m != null) {
            val n = m.groupValues[1].toLongOrNull() ?: return null
            val u = m.groupValues[2]
            return when {
                u.startsWith("сек")   -> now.plusSeconds(n)
                u.startsWith("мин")   -> now.plusMinutes(n)
                u.startsWith("час")   -> now.plusHours(n)
                u.startsWith("нед")   -> now.plusWeeks(n)
                u.startsWith("месяц") -> now.plusDays(30L * n)
                u.startsWith("год")   -> now.plusDays(365L * n)
                else                  -> now.plusDays(n)
            }
        }

        // "через час/минуту/день/неделю..."
        val m2 = Regex("\\bчерез\\s+(секунд|секунду|секунды|минут|минуту|минуты|час|часа|часов|день|дня|сутки|недел|неделю|недели|месяц|месяца|месяцев|год|года|лет)\\b").find(t)
            if (m2 != null) {
            val u = m2.groupValues[1]
            return when {
                u.startsWith("сек")   -> now.plusSeconds(1)
                u.startsWith("мин")   -> now.plusMinutes(1)
                u.startsWith("час")   -> now.plusHours(1)
                u.startsWith("нед")   -> now.plusWeeks(1)
                u.startsWith("месяц") -> now.plusDays(30)
                u.startsWith("год")   -> now.plusDays(365)
                else                  -> now.plusDays(1)
            }
        }

        return null
    }

    // ─── detectBaseDate ─────────────────────────────────────────────────────

    private fun detectBaseDate(t: String, today: LocalDate): LocalDate? {
        when {
            t.contains("послезавтра") -> return today.plusDays(2)
            t.contains("завтра")      -> return today.plusDays(1)
            t.contains("сегодня")     -> return today
        }

        parseExplicitDate(t, today)?.let { return it }

        val wd = pickWeekdayByTextOrder(t) ?: return null
        return weekdayToNextDate(wd, today)
    }

    private fun pickWeekdayByTextOrder(t: String): String? {
        val hits = mutableListOf<Pair<Int, String>>()
        for (w in (RU_WEEKDAYS + RU_WEEKDAYS_ACC)) {
            val m = Regex("\\b${Regex.escape(w)}\\b").find(t)
            if (m != null) hits += (m.range.first to w)
        }
        if (hits.isEmpty()) return null
        return hits.minBy { it.first }.second
    }

    private fun weekdayToNextDate(w: String, today: LocalDate): LocalDate? {
        val map = mutableMapOf<String, Int>()
        RU_WEEKDAYS.forEachIndexed { i, s -> map[s] = i }
        RU_WEEKDAYS_ACC.forEachIndexed { i, s -> map[s] = i }
        val target = map[w] ?: return null
        val cur = today.dayOfWeek.value - 1 // Mon=0..Sun=6
        var daysAhead = (target - cur) % 7
        if (daysAhead <= 0) daysAhead += 7
        return today.plusDays(daysAhead.toLong())
    }

    private fun parseExplicitDate(t: String, today: LocalDate): LocalDate? {

        // "первое апреля" / "двадцать третьего мая" — порядковые числительные
        val ordinals = mapOf(
            "первого" to 1,  "первое" to 1,
            "второго" to 2,  "второе" to 2,
            "третьего" to 3, "третье" to 3,
            "четвертого" to 4, "четвертое" to 4,
            "пятого" to 5,   "пятое" to 5,
            "шестого" to 6,  "шестое" to 6,
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
        val mOrd = Regex("\\b(двадцать\\s+\\w+|тридцать\\s+\\w+|\\w+)\\s+(январ|феврал|март|апрел|мая?|июн|июл|август|сентябр|октябр|ноябр|декабр)\\w*\\b").find(t)
        if (mOrd != null) {
            val dayWord = mOrd.groupValues[1].trim()
            val day = ordinals[dayWord]
            if (day != null) {
                val mw = mOrd.groupValues[2]
                val month = when {
                    mw.startsWith("январ")   -> 1
                    mw.startsWith("феврал")  -> 2
                    mw.startsWith("март")    -> 3
                    mw.startsWith("апрел")   -> 4
                    mw.startsWith("ма")      -> 5
                    mw.startsWith("июн")     -> 6
                    mw.startsWith("июл")     -> 7
                    mw.startsWith("август")  -> 8
                    mw.startsWith("сентябр") -> 9
                    mw.startsWith("октябр")  -> 10
                    mw.startsWith("ноябр")   -> 11
                    mw.startsWith("декабр")  -> 12
                    else                     -> return null
                }
                var d = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
                if (d.isBefore(today)) d = runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull() ?: return null
                return d
            }
        }

        // "13 февраля"
        val m1 = Regex("\\b(\\d{1,2})\\s+(январ|феврал|март|апрел|мая?|июн|июл|август|сентябр|октябр|ноябр|декабр)\\w*\\b")
            .find(t)

        if (m1 != null) {
            val day = m1.groupValues[1].toIntOrNull() ?: return null
            val mw  = m1.groupValues[2]
            val month = when {
                mw.startsWith("январ")   -> 1
                mw.startsWith("феврал")  -> 2
                mw.startsWith("март")    -> 3
                mw.startsWith("апрел")   -> 4
                mw.startsWith("ма")      -> 5
                mw.startsWith("июн")     -> 6
                mw.startsWith("июл")     -> 7
                mw.startsWith("август")  -> 8
                mw.startsWith("сентябр") -> 9
                mw.startsWith("октябр")  -> 10
                mw.startsWith("ноябр")   -> 11
                mw.startsWith("декабр")  -> 12
                else                     -> return null
            }
            var d = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
            if (d.isBefore(today)) d = runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull() ?: return null
            return d
        }

        // "13.02" / "13/02/2026"
        val m2 = Regex("\\b(\\d{1,2})[./-](\\d{1,2})(?:[./-](\\d{2,4}))?\\b").find(t)
        if (m2 != null) {
            val day   = m2.groupValues[1].toIntOrNull() ?: return null
            val month = m2.groupValues[2].toIntOrNull() ?: return null
            val yRaw  = m2.groupValues.getOrNull(3).orEmpty()
            val year  = if (yRaw.isNotBlank()) {
                val y = yRaw.toInt()
                if (y < 100) 2000 + y else y
            } else today.year

            var d = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
            if (yRaw.isBlank() && d.isBefore(today)) {
                d = runCatching { LocalDate.of(year + 1, month, day) }.getOrNull() ?: return null
            }
            return d
        }
        return null
    }
    // ─── parseTimeToken ─────────────────────────────────────────────────────

    private fun parseTimeToken(t: String): LocalTime? {
        fun hasMorning() = t.contains("утр")
        fun hasDay() = t.contains("днем") || t.contains("днём") || t.contains("дня") || t.contains("полдень")
        fun hasEvening() = t.contains("веч")
        fun hasNight()   = t.contains("ноч")

        fun applyDaypart(h: Int): Int {
            val hour = h.coerceIn(0, 23)

            return when {
                hasMorning() -> {
                    when (hour) {
                        12 -> 0
                        in 1..11 -> hour
                        else -> hour
                    }
                }

                hasDay() -> {
                    when (hour) {
                        12 -> 12
                        in 1..7 -> hour + 12
                        else -> hour
                    }
                }

                hasEvening() -> {
                    when (hour) {
                        12 -> 12
                        in 1..11 -> hour + 12
                        else -> hour
                    }
                }

                hasNight() -> {
                    when (hour) {
                        12 -> 0
                        in 1..5 -> hour
                        in 6..11 -> hour + 12
                        else -> hour
                    }
                }

                else -> {
                    // Если время однозначное (< 12) и уже прошло сегодня —
                    // скорее всего имеется в виду вечер
                    if (hour in 1..11) {
                        val currentHour = java.time.LocalTime.now().hour
                        if (currentHour >= 12 && hour < currentHour) {
                            hour + 12
                        } else {
                            hour
                        }
                    } else {
                        hour
                    }
                }
            }
        }

        fun clamp(h: Int, m: Int): LocalTime =
            LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59))

        if (t.contains("полдень"))  return LocalTime.of(12, 0)
        if (t.contains("полночь")) return LocalTime.of(0, 0)

        // 18:30 / 06.15 / 5:30 утром
        Regex("\\b(\\d{1,2})[:.](\\d{2})\\b").find(t)?.let { m ->
            val rawHour = m.groupValues[1].toIntOrNull() ?: return null
            val minute  = m.groupValues[2].toIntOrNull() ?: return null
            val hour    = applyDaypart(rawHour)
            return clamp(hour, minute)
        }

        // "без 10 6"
        Regex("\\bбез\\s+(\\d{1,2})\\s*(?:минут[ы]?|мин)?\\s+(\\d{1,2})\\b").find(t)?.let { m ->
            val minus = m.groupValues[1].toInt().coerceIn(0, 59)
            val nextH = applyDaypart(m.groupValues[2].toInt())
            val mi    = 60 - minus
            val h     = if (mi != 0) nextH - 1 else nextH
            return clamp(h, mi)
        }

        // "без четверти восемь"
        Regex("\\bбез\\s+четверти\\s+(\\d{1,2})\\b").find(t)?.let { m ->
            val nextH = applyDaypart(m.groupValues[1].toInt())
            return clamp(nextH - 1, 45)
        }

        // "три четверти восьмого"
        Regex("\\bтри\\s+четверти\\s+(\\d{1,2})\\b").find(t)?.let { m ->
            val nextH = applyDaypart(m.groupValues[1].toInt())
            return clamp(nextH - 1, 45)
        }
        // "четверть восьмого"
        Regex("\\bчетверть\\s+(\\d{1,2})\\b").find(t)?.let { m ->
            if (!t.contains("без четверти")) {
                val nextH = applyDaypart(m.groupValues[1].toInt())
                return clamp(nextH - 1, 15)
            }
        }

        // "полвосьмого"
        Regex("\\bпол(\\d{1,2})\\b").find(t)?.let { m ->
            val nextH = applyDaypart(m.groupValues[1].toInt())
            return clamp(nextH - 1, 30)
        }

        // "в 6" / "к 6" / "на 7 утра"
        Regex("(?:\\bв\\b|\\bк\\b|\\bна\\b)\\s*(\\d{1,2})(?:\\s*час[аов]?)?\\b").find(t)?.let { m ->
            val rawHour = m.groupValues[1].toIntOrNull() ?: return null
            val hour = applyDaypart(rawHour)
            return clamp(hour, 0)
        }

        // "в 15 30" / "в 9 00" — предлог + час + минуты без разделителя
        Regex("(?:\\bв\\b|\\bк\\b)\\s*(\\d{1,2})\\s+(\\d{2})\\b").find(t)?.let { m ->
            val rawHour = m.groupValues[1].toIntOrNull() ?: return null
            val minute  = m.groupValues[2].toIntOrNull() ?: return null
            if (minute in 0..59) {
                val hour = applyDaypart(rawHour)
                return clamp(hour, minute)
            }
        }

        // "15 часов" / "9 часов утра" — число + «час» без предлога
        Regex("\\b(\\d{1,2})\\s*час[аов]\\b").find(t)?.let { m ->
            val rawHour = m.groupValues[1].toIntOrNull() ?: return null
            val hour = applyDaypart(rawHour)
            return clamp(hour, 0)
        }

        // "15 30" / "9 15" — два числа подряд (час + минуты), без предлога и разделителя
        // Проверяем что второе число — валидные минуты (00-59)
        Regex("\\b(\\d{1,2})\\s+(\\d{2})\\b").find(t)?.let { m ->
            val rawHour = m.groupValues[1].toIntOrNull() ?: return null
            val minute  = m.groupValues[2].toIntOrNull() ?: return null
            if (rawHour in 0..23 && minute in 0..59) {
                val hour = applyDaypart(rawHour)
                return clamp(hour, minute)
            }
        }

        // "15" / "9" — одно число без контекста, только если нет других совпадений
        Regex("\\b(\\d{1,2})\\b").find(t)?.let { m ->
            val rawHour = m.groupValues[1].toIntOrNull() ?: return null
            if (rawHour in 0..23) {
                val hour = applyDaypart(rawHour)
                return clamp(hour, 0)
            }
        }
        return null

        // Числительные прописью: "восемь", "девять вечера", "в десять утра"
        val wordHours = mapOf(
            "один" to 1, "раз" to 1,
            "два" to 2, "две" to 2,
            "три" to 3,
            "четыре" to 4,
            "пять" to 5,
            "шесть" to 6,
            "семь" to 7,
            "восемь" to 8,
            "девять" to 9,
            "десять" to 10,
            "одиннадцать" to 11,
            "двенадцать" to 12,
            "тринадцать" to 13,
            "четырнадцать" to 14,
            "пятнадцать" to 15,
            "шестнадцать" to 16,
            "семнадцать" to 17,
            "восемнадцать" to 18,
            "девятнадцать" to 19,
            "двадцать" to 20,
            "двадцать один" to 21,
            "двадцать два" to 22,
            "двадцать три" to 23,
            "ноль" to 0, "нуль" to 0
        )
        for ((word, h) in wordHours.entries.sortedByDescending { it.key.length }) {
            val regex = Regex("(?:\\bв\\b|\\bк\\b|\\bна\\b)?\\s*\\b${Regex.escape(word)}\\b")
            if (regex.containsMatchIn(t)) {
                val hour = applyDaypart(h)
                return clamp(hour, 0)
            }
        }

        return null
    }

    /**
     * Парсит диапазоны:
     * - суточный: «с 9:00 до 18:00», «с 9 до 6 вечера»
     * - по датам: «с 1 апреля по 30 апреля», «с завтра по пятницу»
     */
    private fun parseRepeatRange(t: String, now: LocalDateTime): RepeatRangeResult? {
        val zone = ZoneId.systemDefault()

        // Суточный диапазон: "с 9:00 до 18:00" / "с 9 до 18"
        val dayRangeRegex = Regex("""с\s+(\d{1,2})(?::(\d{2}))?\s+до\s+(\d{1,2})(?::(\d{2}))?""")
        dayRangeRegex.find(t)?.let { m ->
            val h1 = m.groupValues[1].toIntOrNull() ?: return@let
            val m1 = m.groupValues[2].toIntOrNull() ?: 0
            val h2 = m.groupValues[3].toIntOrNull() ?: return@let
            val m2 = m.groupValues[4].toIntOrNull() ?: 0
            if (h1 in 0..23 && h2 in 0..23) {
                return RepeatRangeResult(
                    dayRangeStart = h1 * 3600 + m1 * 60,
                    dayRangeEnd   = h2 * 3600 + m2 * 60
                )
            }
        }

        // Диапазон по датам: "с 1 апреля по 30 апреля"
        val dateRangeRegex = Regex("""с\s+(.+?)\s+по\s+(.+)""")
        dateRangeRegex.find(t)?.let { m ->
            val fromStr = m.groupValues[1].trim()
            val toStr   = m.groupValues[2].trim()
            val today   = now.toLocalDate()

            val fromDate = parseSimpleDate(fromStr, today)
            val toDate   = parseSimpleDate(toStr, today)

            if (fromDate != null && toDate != null) {
                return RepeatRangeResult(
                    repeatFrom  = fromDate.atTime(6, 0),
                    repeatUntil = toDate.atTime(23, 59)
                )
            }
        }

        return null
    }

    /** Парсит простую дату: "1 апреля", "завтра", "пятница" */
    private fun parseSimpleDate(s: String, today: LocalDate): LocalDate? {
        val t = s.trim()
        when (t) {
            "сегодня"     -> return today
            "завтра"      -> return today.plusDays(1)
            "послезавтра" -> return today.plusDays(2)
        }

        val ordinalMap = mapOf(
            "первого" to 1, "первое" to 1,
            "второго" to 2, "третьего" to 3, "четвертого" to 4,
            "пятого" to 5, "шестого" to 6, "седьмого" to 7,
            "восьмого" to 8, "девятого" to 9, "десятого" to 10,
            "одиннадцатого" to 11, "двенадцатого" to 12,
            "тринадцатого" to 13, "четырнадцатого" to 14,
            "пятнадцатого" to 15, "шестнадцатого" to 16,
            "семнадцатого" to 17, "восемнадцатого" to 18,
            "девятнадцатого" to 19, "двадцатого" to 20,
            "двадцать первого" to 21, "двадцать второго" to 22,
            "двадцать третьего" to 23, "двадцать четвертого" to 24,
            "двадцать пятого" to 25, "двадцать шестого" to 26,
            "двадцать седьмого" to 27, "двадцать восьмого" to 28,
            "двадцать девятого" to 29, "тридцатого" to 30,
            "тридцать первого" to 31
        )

        val monthMap = mapOf(
            "январ" to 1, "феврал" to 2, "март" to 3, "апрел" to 4,
            "мая" to 5, "май" to 5, "июн" to 6, "июл" to 7,
            "август" to 8, "сентябр" to 9, "октябр" to 10,
            "ноябр" to 11, "декабр" to 12
        )

        // Определяем день — сначала прописью
        var day: Int? = null
        for ((word, num) in ordinalMap.entries.sortedByDescending { it.key.length }) {
            if (t.contains(word)) { day = num; break }
        }

        // Потом цифрой
        if (day == null) {
            day = Regex("""^(\d{1,2})""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        }

        if (day == null || day !in 1..31) return null

        val month = monthMap.entries.firstOrNull { t.contains(it.key) }?.value ?: return null

        var date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        if (date.isBefore(today)) date = date.plusYears(1)
        return date
    }

    private data class RepeatRangeResult(
        val repeatFrom: LocalDateTime? = null,
        val repeatUntil: LocalDateTime? = null,
        val dayRangeStart: Int? = null,
        val dayRangeEnd: Int? = null
    )

    /**
     * Конвертирует часть суток в конкретное время.
     * утро → 6:00, день → 11:00, вечер → 18:00, ночь → 22:00
     */
    private fun parsePartOfDayTime(t: String): LocalTime? {
        return when {
            t.contains("утром") || t.contains("утра") || t.contains("с утра") ->
                LocalTime.of(6, 0)
            t.contains("днем") || t.contains("днём") || t.contains("дня") ->
                LocalTime.of(11, 0)
            t.contains("вечером") || t.contains("вечера") || t.contains("на вечер") ->
                LocalTime.of(18, 0)
            t.contains("ночью") || t.contains("ночи") || t.contains("на ночь") ->
                LocalTime.of(22, 0)
            else -> null
        }
    }
}
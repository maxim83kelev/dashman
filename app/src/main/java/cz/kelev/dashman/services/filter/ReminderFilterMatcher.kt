package cz.kelev.dashman.services.filter

import cz.kelev.dashman.storage.ReminderEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object ReminderFilterMatcher {

    fun matches(
        reminder: ReminderEntity,
        filter: ReminderFilter,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        return when (filter) {
            is ReminderFilter.All      -> true
            is ReminderFilter.ByIds    -> reminder.id in filter.ids
            is ReminderFilter.ByPriority -> reminder.priority == filter.priority

            is ReminderFilter.Today ->
                matchesDate(reminder, today, zoneId)

            is ReminderFilter.Tomorrow ->
                matchesDate(reminder, today.plusDays(1), zoneId)

            is ReminderFilter.DayAfterTomorrow ->
                matchesDate(reminder, today.plusDays(2), zoneId)

            is ReminderFilter.InDays -> {
                if (filter.daysAhead < 0) return false
                matchesDate(reminder, today.plusDays(filter.daysAhead.toLong()), zoneId)
            }

            is ReminderFilter.ThisWeek -> {
                val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val end   = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                (start..end).any { matchesDate(reminder, it, zoneId) }
            }

            is ReminderFilter.ThisMonth -> {
                val start = today.withDayOfMonth(1)
                val end   = today.withDayOfMonth(today.month.length(today.isLeapYear))
                (start..end).any { matchesDate(reminder, it, zoneId) }
            }

            is ReminderFilter.ThisWeekend -> {
                val saturday = if (today.dayOfWeek == DayOfWeek.SATURDAY ||
                                   today.dayOfWeek == DayOfWeek.SUNDAY) {
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
                } else {
                    today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                }
                val sunday = saturday.plusDays(1)
                matchesDate(reminder, saturday, zoneId) || matchesDate(reminder, sunday, zoneId)
            }

            is ReminderFilter.ByWeekday ->
                matchesDate(reminder, nextOrSameWeekday(today, filter.dayOfWeek), zoneId)

            is ReminderFilter.ByExactDate ->
                matchesDate(reminder, filter.date, zoneId)

            is ReminderFilter.ByMonth -> {
                val start = LocalDate.of(filter.year, filter.month, 1)
                val end   = start.withDayOfMonth(start.month.length(start.isLeapYear))
                (start..end).any { matchesDate(reminder, it, zoneId) }
            }

            is ReminderFilter.PartOfDay.Morning -> {
                val time = reminder.localTimeOrNull(zoneId) ?: return false
                time in MORNING_RANGE
            }
            is ReminderFilter.PartOfDay.Afternoon -> {
                val time = reminder.localTimeOrNull(zoneId) ?: return false
                time in AFTERNOON_RANGE
            }
            is ReminderFilter.PartOfDay.Evening -> {
                val time = reminder.localTimeOrNull(zoneId) ?: return false
                time in EVENING_RANGE
            }
            is ReminderFilter.PartOfDay.Night -> {
                val time = reminder.localTimeOrNull(zoneId) ?: return false
                time in NIGHT_RANGE_1 || time in NIGHT_RANGE_2
            }

            is ReminderFilter.Combined ->
                matches(reminder, filter.base, today, zoneId) &&
                matches(reminder, filter.partOfDay, today, zoneId)
        }
    }

    // ─── Главная логика матчинга по дате ──────────────────────────────────────
    /**
     * Проверяет, относится ли напоминание к конкретному дню с учётом repeat.
     *
     * Логика:
     * 1. Если repeat есть — проверяем, попадает ли targetDate в правило повтора.
     *    dueAt используется только как время срабатывания (час:минута), не как дата.
     * 2. Если repeat нет — сравниваем dueAt.date == targetDate как раньше.
     */
    private fun matchesDate(
        reminder: ReminderEntity,
        targetDate: LocalDate,
        zoneId: ZoneId
    ): Boolean {
        val repeat = reminder.repeat

        // Нет repeat — старая логика, просто сравниваем дату
        if (repeat.isNullOrBlank()) {
            val dueDate = reminder.localDateOrNull(zoneId) ?: return false
            return dueDate == targetDate
        }

        // Проверяем repeatFrom / repeatUntil если есть
        val repeatFrom = reminder.repeatFrom?.let {
            Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
        }
        val repeatUntil = reminder.repeatUntil?.let {
            Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
        }

        if (repeatFrom != null && targetDate.isBefore(repeatFrom)) return false
        if (repeatUntil != null && targetDate.isAfter(repeatUntil)) return false

        // Матчим по правилу повтора
        return when {
            repeat == "weekdays" ->
                targetDate.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

            repeat == "weekends" ->
                targetDate.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

            repeat == "monthly" -> {
                val dueDate = reminder.localDateOrNull(zoneId) ?: return false
                targetDate.dayOfMonth == dueDate.dayOfMonth
            }

            repeat.startsWith("every_") -> {
                val dueDate = reminder.localDateOrNull(zoneId) ?: return false
                matchesEveryRule(repeat, dueDate, targetDate)
            }

            else -> false
        }
    }

    /**
     * Матчит правила вида every_N_days / every_N_weeks / every_N_hours / every_N_minutes.
     * Часовые/минутные правила — показываем каждый день (срабатывают много раз).
     */
    private fun matchesEveryRule(
        repeat: String,
        dueDate: LocalDate,
        targetDate: LocalDate
    ): Boolean {
        // every_N_minutes / every_N_hours — срабатывает каждый день
        if (repeat.endsWith("_minutes") || repeat.endsWith("_hours")) return true

        val parts = repeat.split("_") // ["every", "N", "days"/"weeks"]
        if (parts.size < 3) return false
        val n = parts[1].toLongOrNull() ?: return false

        return when (parts[2]) {
            "days" -> {
                val diff = java.time.temporal.ChronoUnit.DAYS.between(dueDate, targetDate)
                diff >= 0 && diff % n == 0L
            }
            "weeks" -> {
                val diff = java.time.temporal.ChronoUnit.WEEKS.between(dueDate, targetDate)
                diff >= 0 && diff % n == 0L &&
                dueDate.dayOfWeek == targetDate.dayOfWeek
            }
            else -> false
        }
    }

    // ─── Хелперы ──────────────────────────────────────────────────────────────

    /** Ближайший день недели >= today */
    private fun nextOrSameWeekday(today: LocalDate, dow: DayOfWeek): LocalDate =
        today.with(TemporalAdjusters.nextOrSame(dow))

    /** Итератор для диапазона дат */
    private operator fun ClosedRange<LocalDate>.iterator(): Iterator<LocalDate> =
        generateSequence(start) { if (it < endInclusive) it.plusDays(1) else null }.iterator()

    /** Итерация по диапазону дат через any{} */
    private fun ClosedRange<LocalDate>.any(predicate: (LocalDate) -> Boolean): Boolean {
        var current = start
        while (!current.isAfter(endInclusive)) {
            if (predicate(current)) return true
            current = current.plusDays(1)
        }
        return false
    }
    private fun ReminderEntity.localDateOrNull(zoneId: ZoneId): LocalDate? {
        val millis = dueAt ?: return null
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
    }

    private fun ReminderEntity.localTimeOrNull(zoneId: ZoneId): LocalTime? {
        val millis = dueAt ?: return null
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalTime()
    }

    private val MORNING_RANGE   = LocalTime.of(5,  0)..LocalTime.of(11, 59)
    private val AFTERNOON_RANGE = LocalTime.of(12, 0)..LocalTime.of(16, 59)
    private val EVENING_RANGE   = LocalTime.of(17, 0)..LocalTime.of(22, 59)
    private val NIGHT_RANGE_1   = LocalTime.of(23, 0)..LocalTime.of(23, 59)
    private val NIGHT_RANGE_2   = LocalTime.of(0,  0)..LocalTime.of(4,  59)
}
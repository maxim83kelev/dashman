package cz.kelev.dashman.services.filter

import java.time.DayOfWeek
import java.time.LocalDate

sealed class ReminderFilter {
    data object All : ReminderFilter()

    data object Today : ReminderFilter()
    data object Tomorrow : ReminderFilter()
    data object DayAfterTomorrow : ReminderFilter()

    data class InDays(val daysAhead: Int) : ReminderFilter()

    data object ThisWeek : ReminderFilter()
    data object ThisMonth : ReminderFilter()
    data object ThisWeekend : ReminderFilter()

    data class ByWeekday(val dayOfWeek: DayOfWeek) : ReminderFilter()
    data class ByExactDate(val date: LocalDate) : ReminderFilter()
    data class ByMonth(val month: Int, val year: Int) : ReminderFilter() {
        init {
            require(month in 1..12) { "month must be in 1..12, got $month" }
        }
    }

    /** Показать только конкретные напоминания по их ID — используется при удалении */
    data class ByIds(val ids: Set<Long>) : ReminderFilter()

    /** Показать напоминания по приоритету: "important", "critical", "normal" */
    data class ByPriority(val priority: String) : ReminderFilter()

    sealed class PartOfDay : ReminderFilter() {
        data object Morning   : PartOfDay()
        data object Afternoon : PartOfDay()
        data object Evening   : PartOfDay()
        data object Night     : PartOfDay()
    }

    data class Combined(
        val base: ReminderFilter,
        val partOfDay: PartOfDay
    ) : ReminderFilter()
}

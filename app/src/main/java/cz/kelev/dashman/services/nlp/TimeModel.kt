package cz.kelev.dashman.services.nlp

import java.time.LocalDateTime

sealed class NeedClarification {
    data class Time(val message: String = "Не понял время. Скажи, например: завтра в 7:30.") : NeedClarification()
    data class Unit(val n: Int) : NeedClarification()
}

sealed class RepeatRule {
    data object Daily    : RepeatRule()
    data object Weekdays : RepeatRule()
    data object Weekend  : RepeatRule()
    data object Monthly  : RepeatRule()  // ← новый
    data class Every(val n: Int, val unit: Unit) : RepeatRule()

    enum class Unit { MINUTE, HOUR, DAY, WEEK }
}

data class TimeParseResult(
    val whenDt: LocalDateTime?,
    val repeat: RepeatRule?,
    val need: NeedClarification?,
    val repeatFrom: LocalDateTime? = null,
    val repeatUntil: LocalDateTime? = null,
    val dayRangeStart: Int? = null,
    val dayRangeEnd: Int? = null
)

// Строки точно совпадают с форматами RepeatCalculator.next()
fun RepeatRule.toStorageString(): String = when (this) {
    RepeatRule.Daily         -> "every_1_days"
    RepeatRule.Weekdays      -> "weekdays"
    RepeatRule.Weekend       -> "weekends"
    RepeatRule.Monthly       -> "monthly"
    is RepeatRule.Every      -> when (unit) {
        RepeatRule.Unit.MINUTE -> "every_${n}_minutes"
        RepeatRule.Unit.HOUR   -> "every_${n}_hours"
        RepeatRule.Unit.DAY    -> "every_${n}_days"
        RepeatRule.Unit.WEEK   -> "every_${n}_weeks"
    }
}
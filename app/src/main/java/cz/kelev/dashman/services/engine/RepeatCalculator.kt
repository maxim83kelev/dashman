package cz.kelev.dashman.services.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import cz.kelev.dashman.services.nlp.RepeatRule

/**
 * Форматы строки repeat, которые хранятся в ReminderEntity.repeat:
 *
 *  every_N_minutes   — каждые N минут
 *  every_N_hours     — каждые N часов
 *  every_N_days      — каждые N дней
 *  every_N_weeks     — каждые N недель
 *  weekdays          — каждый будний день (пн–пт)
 *  weekends          — каждые выходные (сб–вс)
 *  monthly           — каждый месяц, тот же день числа
 *
 * repeatFrom — начало диапазона (миллисекунды):
 *   - null: повторять сразу
 *   - время: не раньше этого момента
 *
 * repeatUntil — конец диапазона (миллисекунды):
 *   - null: повторять бесконечно
 *   - время: не позже этого момента
 *
 * Два режима:
 *   1. Диапазон по датам: «с 1 апреля по 30 апреля» — repeatFrom/repeatUntil это даты
 *   2. Диапазон в сутках: «с 9:00 до 18:00» — repeatFrom/repeatUntil это время суток
 *      хранится как смещение в секундах от начала дня (0..86399)
 *      помечается префиксом "tod:" в строке repeat, например "every_2_hours|tod:32400:64800"
 */
object RepeatCalculator {

    /**
     * Возвращает следующий момент срабатывания либо null если:
     * - формат repeat неизвестен
     * - следующее время выходит за repeatUntil
     */
    fun next(
        repeat: String,
        from: Long,
        repeatFrom: Long? = null,
        repeatUntil: Long? = null
    ): Long? {
        val zone = ZoneId.systemDefault()

        // Разбираем repeat — может содержать суточный диапазон
        val (pureRepeat, dayStart, dayEnd) = parseDayRange(repeat)

        val fromDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(from), zone)

        val nextDt: LocalDateTime = when {

            pureRepeat.matches(Regex("every_\\d+_minutes")) -> {
                val n = pureRepeat.split("_")[1].toLongOrNull() ?: return null
                fromDt.plusMinutes(n)
            }

            pureRepeat.matches(Regex("every_\\d+_hours")) -> {
                val n = pureRepeat.split("_")[1].toLongOrNull() ?: return null
                fromDt.plusHours(n)
            }

            pureRepeat.matches(Regex("every_\\d+_days")) -> {
                val n = pureRepeat.split("_")[1].toLongOrNull() ?: return null
                fromDt.plusDays(n)
            }

            pureRepeat.matches(Regex("every_\\d+_weeks")) -> {
                val n = pureRepeat.split("_")[1].toLongOrNull() ?: return null
                fromDt.plusWeeks(n)
            }

            pureRepeat == "weekdays" -> {
                var next = fromDt.plusDays(1)
                while (next.dayOfWeek == DayOfWeek.SATURDAY ||
                       next.dayOfWeek == DayOfWeek.SUNDAY) {
                    next = next.plusDays(1)
                }
                next
            }

            pureRepeat == "weekends" -> {
                val nextSat = fromDt.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
                val nextSun = fromDt.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                if (nextSat.isBefore(nextSun)) nextSat else nextSun
            }

            pureRepeat == "monthly" -> fromDt.plusMonths(1)

            else -> return null
        }

        var nextMillis = nextDt.atZone(zone).toInstant().toEpochMilli()

        // Применяем суточный диапазон (с 9:00 до 18:00)
        if (dayStart != null && dayEnd != null) {
            nextMillis = adjustToDayRange(nextMillis, dayStart, dayEnd, zone) ?: return null
        }

        // Проверяем repeatFrom — не раньше начала
        if (repeatFrom != null && nextMillis < repeatFrom) {
            nextMillis = repeatFrom
        }

        // Проверяем repeatUntil — не позже конца
        if (repeatUntil != null && nextMillis > repeatUntil) {
            return null // повторения закончились
        }

        return nextMillis
    }
    /**
     * Корректирует время под суточный диапазон.
     * Если следующее время попадает вне диапазона — сдвигаем на начало диапазона следующего дня.
     */
    private fun adjustToDayRange(
        millis: Long,
        dayStartSec: Int,
        dayEndSec: Int,
        zone: ZoneId
    ): Long? {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        val secondOfDay = dt.toLocalTime().toSecondOfDay()

        return when {
            secondOfDay in dayStartSec..dayEndSec -> millis
            secondOfDay < dayStartSec -> {
                // Раньше начала — сдвигаем на начало этого же дня
                val adjusted = dt.toLocalDate()
                    .atTime(LocalTime.ofSecondOfDay(dayStartSec.toLong()))
                adjusted.atZone(zone).toInstant().toEpochMilli()
            }
            else -> {
                // Позже конца — сдвигаем на начало следующего дня
                val adjusted = dt.toLocalDate().plusDays(1)
                    .atTime(LocalTime.ofSecondOfDay(dayStartSec.toLong()))
                adjusted.atZone(zone).toInstant().toEpochMilli()
            }
        }
    }

    /**
     * Парсит строку repeat на чистый repeat и суточный диапазон.
     * Формат с диапазоном: "every_2_hours|tod:32400:64800"
     * (32400 = 9*3600 = 9:00, 64800 = 18*3600 = 18:00)
     */
    private fun parseDayRange(repeat: String): Triple<String, Int?, Int?> {
        if (!repeat.contains("|tod:")) return Triple(repeat, null, null)
        val parts = repeat.split("|tod:")
        val pureRepeat = parts[0]
        val rangeParts = parts[1].split(":")
        val start = rangeParts.getOrNull(0)?.toIntOrNull()
        val end = rangeParts.getOrNull(1)?.toIntOrNull()
        return Triple(pureRepeat, start, end)
    }

    // ─── Фабричные методы ──────────────────────────────────────────────────

    fun everyMinutes(n: Int) = "every_${n}_minutes"
    fun everyHours(n: Int)   = "every_${n}_hours"
    fun everyDays(n: Int)    = "every_${n}_days"
    fun everyWeeks(n: Int)   = "every_${n}_weeks"
    fun weekdays()           = "weekdays"
    fun weekends()           = "weekends"
    fun monthly()            = "monthly"

    /**
     * Создаёт строку repeat с суточным диапазоном.
     * Например: everyHoursInRange(2, 9, 0, 18, 0) = "every_2_hours|tod:32400:64800"
     */
    fun withDayRange(repeat: String, fromHour: Int, fromMin: Int, toHour: Int, toMin: Int): String {
        val startSec = fromHour * 3600 + fromMin * 60
        val endSec   = toHour * 3600 + toMin * 60
        return "$repeat|tod:$startSec:$endSec"
    }

    // ─── Описание для UI ───────────────────────────────────────────────────

    fun describe(repeat: String): String {
        val (pure, dayStart, dayEnd) = parseDayRange(repeat)
        val base = when {
            pure.matches(Regex("every_\\d+_minutes")) -> "каждые ${pure.split("_")[1]} мин."
            pure.matches(Regex("every_\\d+_hours"))   -> "каждые ${pure.split("_")[1]} ч."
            pure.matches(Regex("every_\\d+_days"))    -> "каждые ${pure.split("_")[1]} дн."
            pure.matches(Regex("every_\\d+_weeks"))   -> "каждые ${pure.split("_")[1]} нед."
            pure == "weekdays" -> "каждый будний день"
            pure == "weekends" -> "каждые выходные"
            pure == "monthly"  -> "каждый месяц"
            else -> pure
        }
        return if (dayStart != null && dayEnd != null) {
            val h1 = dayStart / 3600
            val m1 = (dayStart % 3600) / 60
            val h2 = dayEnd / 3600
            val m2 = (dayEnd % 3600) / 60
            "$base с %02d:%02d до %02d:%02d".format(h1, m1, h2, m2)
        } else base
    }
}
package cz.kelev.dashman.services.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import cz.kelev.dashman.services.ReminderReceiver
import cz.kelev.dashman.services.nlp.TaskTextExtractor
import cz.kelev.dashman.services.nlp.TimeAndRepeatParser
import cz.kelev.dashman.services.nlp.NeedClarification
import cz.kelev.dashman.services.nlp.toStorageString
import cz.kelev.dashman.storage.ReminderEntity
import cz.kelev.dashman.storage.ReminderPriority
import cz.kelev.dashman.storage.ReminderRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import cz.kelev.dashman.services.nlp.RepeatRule
import cz.kelev.dashman.DashmanLogger

class ReminderEngine(
    private val repo: ReminderRepository,
    private val context: Context
) : ReminderEngineContract {

    override suspend fun createFromText(text: String, now: Long): CreateResult {
        return try {
            val raw = text.trim()
            if (raw.isEmpty()) return CreateResult.Error("Пустой текст напоминания.")

            val zone = ZoneId.systemDefault()
            val nowDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)

            val parsed = TimeAndRepeatParser.parse(raw, nowDt)

            val need = parsed.need
            if (need != null) {
                val r = when (need) {
                    is NeedClarification.Unit ->
                        CreateResult.NeedClarification("Не понял единицу. Скажи: через ${need.n} минут/часов/дней.")
                    is NeedClarification.Time ->
                        CreateResult.NeedClarification(need.message)
                }
                DashmanLogger.d("Dashman", "engine.createFromText needClarification -> $r | raw='$raw'")
                return r
            }

            val whenDt = parsed.whenDt ?: return CreateResult.Error("Не понял, когда напомнить.")
            DashmanLogger.d("Dashman", "parsed.whenDt=${parsed.whenDt} now=$nowDt")
            val dueAt = whenDt.atZone(zone).toInstant().toEpochMilli()

            val content = TaskTextExtractor.extractTaskText(raw).ifBlank { "Без названия" }
            val title = content.take(60)
            val repeatStr = buildRepeatString(parsed)
            val detectedPriority = detectPriority(raw)

            val repeatFromMillis = parsed.repeatFrom?.atZone(zone)?.toInstant()?.toEpochMilli()
            val repeatUntilMillis = parsed.repeatUntil?.atZone(zone)?.toInstant()?.toEpochMilli()
            DashmanLogger.d("Dashman", "repeatFrom=$repeatFromMillis repeatUntil=$repeatUntilMillis dayRangeStart=${parsed.dayRangeStart}")

            val reminderId = repo.add(
                text = content,
                dueAt = dueAt,
                raw = raw,
                title = title,
                priority = detectedPriority,
                repeat = repeatStr,
                status = "active",
                repeatFrom = repeatFromMillis,
                repeatUntil = repeatUntilMillis
            )

            scheduleReminder(reminderId, dueAt)
            DashmanLogger.d("Dashman", "Alarm scheduled for $dueAt id=$reminderId repeat=$repeatStr")

            val r = CreateResult.Success(
                ReminderEntity(
                    id = reminderId,
                    text = content,
                    createdAt = now,
                    dueAt = dueAt,
                    priority = detectedPriority,
                    repeat = repeatStr,
                    status = "active",
                    repeatFrom = repeatFromMillis,
                    repeatUntil = repeatUntilMillis
                )
            )
            DashmanLogger.d(
                "Dashman",
                "engine.createFromText success -> id=$reminderId dueAt=$dueAt repeat=$repeatStr priority=$detectedPriority text='$content'"
            )
            r
        } catch (t: Throwable) {
            DashmanLogger.e("Dashman", "engine.createFromText crash: ${t.message}", t)
            CreateResult.Error("Ошибка сохранения: ${t.message ?: "unknown"}")
        }
    }

    /**
     * Вызывается из ReminderReceiver после того как напоминание сработало.
     * Если у напоминания есть repeat — вычисляет следующее время,
     * обновляет dueAt в базе и планирует новый alarm.
     * Если repeat нет — помечает напоминание как выполненное.
     */
    suspend fun onReminderFired(reminderId: Long) {
        val reminder = repo.getById(reminderId) ?: run {
            DashmanLogger.w("DashmanAlarm", "onReminderFired: reminder $reminderId not found")
            return
        }

        val repeatStr = reminder.repeat
        if (repeatStr.isNullOrBlank()) {
            cancelReminder(reminderId)
            repo.setStatus(reminderId, "fired")
            val updated = repo.getById(reminderId)
            DashmanLogger.d(
                "DashmanAlarm",
                "onReminderFired: one-shot fired id=$reminderId status=${updated?.status} isDone=${updated?.isDone}"
            )
            return
        }
        val firedAt = reminder.dueAt ?: System.currentTimeMillis()
        val nextTime = RepeatCalculator.next(
            repeat = repeatStr,
            from = firedAt,
            repeatFrom = reminder.repeatFrom,
            repeatUntil = reminder.repeatUntil
        )
            if (nextTime == null) {
                cancelReminder(reminderId)
                repo.setStatus(reminderId, "fired")
                DashmanLogger.w("DashmanAlarm", "onReminderFired: unknown repeat='$repeatStr' id=$reminderId, marking fired")
                return
            }

        // Обновляем dueAt в базе и планируем следующий alarm
        val updated = reminder.copy(
            dueAt = nextTime,
            status = "active"
        )
        repo.insert(updated) // update через insert с тем же id (upsert)
        scheduleReminder(reminderId, nextTime)

        DashmanLogger.d("DashmanAlarm",
            "onReminderFired: repeat='$repeatStr' id=$reminderId nextTime=$nextTime " +
            "(${RepeatCalculator.describe(repeatStr)})")
    }

    override fun scheduleReminder(reminderId: Long, triggerAtMillis: Long) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val delta = triggerAtMillis - now

        if (triggerAtMillis <= now) {
            DashmanLogger.w(
                "DashmanAlarm",
                "scheduleReminder skipped: reminderId=$reminderId triggerAtMillis=$triggerAtMillis now=$now delta=$delta"
            )
            return
        }

        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = "cz.kelev.dashman.REMINDER_${reminderId}"
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            (reminderId and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = alarmManager.canScheduleExactAlarms()

        DashmanLogger.d(
            "DashmanAlarm",
            "scheduleReminder: id=$reminderId at=$triggerAtMillis delta=${delta}ms canExact=$canExact"
        )

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelReminder(reminderId: Long) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = "cz.kelev.dashman.REMINDER_${reminderId}"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            (reminderId and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        DashmanLogger.d("DashmanAlarm", "cancelReminder: id=$reminderId")
    }

    suspend fun rescheduleAll() {
        val reminders = repo.getScheduledActive()
        for (rem in reminders) {
            val dueAt = rem.dueAt ?: continue
            if (dueAt > System.currentTimeMillis()) {
                scheduleReminder(rem.id, dueAt)
            } else {
                // Напоминание пропущено — уведомить немедленно
                scheduleReminder(rem.id, System.currentTimeMillis() + 3_000L)
                DashmanLogger.w("DashmanAlarm", "rescheduleAll: missed id=${rem.id}, firing in 3s")
            }
        }
        DashmanLogger.d("DashmanAlarm", "rescheduleAll done: count=${reminders.size}")
    }

    override suspend fun delete(rem: ReminderEntity) {
        cancelReminder(rem.id)
        repo.delete(rem)
    }

    override suspend fun markDone(id: Long) {
        repo.markDone(id)
    }

    override suspend fun cleanupDone(): Int {
        return repo.cleanupDone()
    }

    /**
     * Собирает строку repeat с учётом суточного диапазона.
     * Если есть dayRangeStart/dayRangeEnd — добавляет "|tod:start:end".
     */
    private fun buildRepeatString(parsed: cz.kelev.dashman.services.nlp.TimeParseResult): String? {
        val base = parsed.repeat?.toStorageString() ?: return null
        val s = parsed.dayRangeStart
        val e = parsed.dayRangeEnd
        return if (s != null && e != null) {
            RepeatCalculator.withDayRange(
                repeat = base,
                fromHour = s / 3600,
                fromMin = (s % 3600) / 60,
                toHour = e / 3600,
                toMin = (e % 3600) / 60
            )
        } else base
    }
}
private fun detectPriority(raw: String): String {
        val t = raw.lowercase()

        val criticalKeywords = listOf(
            "пиздец как важно",
            "пиздец важно",
            "срочно",
            "очень важно",
            "максимально важно",
            "критично",
            "обязательно",
            "кровь из носу",
            "не проебать",
            "нельзя забыть",
            "крайний срок",
            "дедлайн",
            "без вариантов",
            "жизненно важно",
            "архиважно"
        )

        val importantKeywords = listOf(
            "важно",
            "обрати внимание",
            "не забудь",
            "желательно",
            "надо бы",
            "стоит",
            "лучше сделать",
            "приоритетно",
            "держи в голове",
            "не затягивай"
        )

        if (criticalKeywords.any { t.contains(it) }) return ReminderPriority.CRITICAL
            if ("не важно" in t) return ReminderPriority.NORMAL
            if (importantKeywords.any { t.contains(it) }) return ReminderPriority.IMPORTANT

            return ReminderPriority.NORMAL
    }
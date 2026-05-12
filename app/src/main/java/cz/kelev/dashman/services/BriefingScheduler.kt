package cz.kelev.dashman.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import cz.kelev.dashman.storage.AppPrefs
import java.util.Calendar

object BriefingScheduler {

    private const val REQUEST_CODE = 2001

    /**
     * Запланировать брифинг на ближайшее наступление заданного времени.
     * Если время сегодня уже прошло — планируем на завтра.
     */
    fun schedule(context: Context, hourOfDay: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val triggerAt = nextTriggerMillis(hourOfDay, minute)
        val pendingIntent = buildPendingIntent(context)

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }

        Log.d("DashmanBriefing", "Briefing scheduled at $hourOfDay:$minute, triggerAt=$triggerAt")
    }

    /**
     * Перепланировать на завтра — вызывается из BriefingReceiver после срабатывания.
     */
    fun rescheduleNext(context: Context) {
        val prefs = AppPrefs(context)
        if (!prefs.getBriefingEnabled()) return

        val (h, m) = prefs.getBriefingTime()
        schedule(context, h, m)
        Log.d("DashmanBriefing", "Briefing rescheduled for tomorrow at $h:$m")
    }

    /**
     * Отменить брифинг.
     */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context))
        Log.d("DashmanBriefing", "Briefing cancelled")
    }

    private fun nextTriggerMillis(hourOfDay: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Если время уже прошло сегодня — переносим на завтра
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BriefingReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
package cz.kelev.dashman.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import cz.kelev.dashman.storage.AppPrefs

object PremiumExpiryNotifier {

    private val NOTIFY_DAYS = listOf(5, 3, 1)

    fun schedule(context: Context) {
        val prefs = AppPrefs(context)
        val status = prefs.getLicenseStatus()
        val expiry = prefs.getLicenseExpiry()

        // Не Premium или бессрочный — ничего не планируем
        if (status != "active" || expiry <= 0L) return

        val now = System.currentTimeMillis() / 1000L
        val daysLeft = ((expiry - now) / 86400).toInt()

        // Срок уже истёк — не планируем
        if (daysLeft <= 0) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        NOTIFY_DAYS.forEach { day ->
            // Планируем только если до этого дня ещё не дошли
            if (daysLeft >= day) {
                val triggerAt = (expiry - day * 86400L) * 1000L // в миллисекундах
                val intent = Intent(context, PremiumExpiryReceiver::class.java).apply {
                    putExtra(PremiumExpiryReceiver.EXTRA_DAYS_LEFT, day)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    day, // уникальный requestCode для каждого уведомления
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } catch (e: SecurityException) {
                    // нет разрешения на точные будильники — ничего страшного
                }
            }
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        NOTIFY_DAYS.forEach { day ->
            val intent = Intent(context, PremiumExpiryReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, day, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
    }
}
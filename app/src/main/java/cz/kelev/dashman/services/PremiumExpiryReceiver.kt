package cz.kelev.dashman.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import cz.kelev.dashman.MainActivity
import cz.kelev.dashman.R

class PremiumExpiryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val daysLeft = intent.getIntExtra(EXTRA_DAYS_LEFT, 0)
        val message = when (daysLeft) {
            5 -> "Через 5 дней истекает Premium — продли чтобы не потерять голосовые функции"
            3 -> "Через 3 дня истекает Premium — не забудь продлить"
            1 -> "Завтра истекает Premium — продли сегодня"
            else -> return
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, daysLeft,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_splash_empty)
            .setContentTitle("⚡ Dashman Premium")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + daysLeft, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Premium статус",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления об истечении Premium"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "dashman_premium_expiry"
        const val EXTRA_DAYS_LEFT = "extra_days_left"
        const val NOTIFICATION_ID_BASE = 3000
    }
}
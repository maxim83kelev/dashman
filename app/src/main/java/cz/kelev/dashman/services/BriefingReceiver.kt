package cz.kelev.dashman.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import cz.kelev.dashman.MainActivity
import cz.kelev.dashman.R

class BriefingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("DashmanBriefing", "BriefingReceiver fired")
        val checker = cz.kelev.dashman.storage.PremiumChecker(
            cz.kelev.dashman.storage.AppPrefs(context)
        )
        if (!checker.isPremium) {
            Log.d("DashmanBriefing", "Briefing blocked: free tier")
            BriefingScheduler.rescheduleNext(context)
            return
        }

        // Интент открытия MainActivity с флагом брифинга
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_FROM_BRIEFING, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_splash_empty)
            .setContentTitle("Дашман")
            .setContentText("Твои задачи на сегодня — загляни.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)

        // Перепланируем на завтра
        BriefingScheduler.rescheduleNext(context)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Брифинг",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ежедневный брифинг задач"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val EXTRA_FROM_BRIEFING = "extra_from_briefing"
        const val CHANNEL_ID = "dashman_briefing"
        const val NOTIFICATION_ID = 2001
        const val REQUEST_CODE = 2001
    }
}
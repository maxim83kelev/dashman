package cz.kelev.dashman.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import cz.kelev.dashman.R
import cz.kelev.dashman.TtsManager
import cz.kelev.dashman.services.engine.ReminderEngine
import cz.kelev.dashman.storage.DashmanDatabase
import cz.kelev.dashman.storage.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReminderExecutionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text       = intent?.getStringExtra(EXTRA_TEXT)       ?: "Напоминание"
        val reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L

        startForeground(NOTIFICATION_ID, buildForegroundNotification(text))

        val tts = TtsManager(this)
        tts.speakNow("Напоминание: $text")

        // Планируем следующее повторение (или помечаем выполненным),
        // пока TTS ещё воспроизводится
        if (reminderId != -1L) {
            scope.launch {
                try {
                    val repo   = ReminderRepository(DashmanDatabase.get(applicationContext).reminderDao())
                    val engine = ReminderEngine(repo, applicationContext)
                    engine.onReminderFired(reminderId)
                } catch (e: Exception) {
                    android.util.Log.e("ReminderExecution", "onReminderFired failed id=$reminderId", e)
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try { tts.shutdown() } catch (_: Throwable) {}
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, 5000L)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dashman reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Foreground notifications for reminder execution"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_splash_empty)
            .setContentTitle("Dashman")
            .setContentText("Выполняю напоминание: $text")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_TEXT        = "extra_text"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"  // <- новый extra
        private const val CHANNEL_ID     = "dashman_reminder_execution"
        private const val NOTIFICATION_ID = 1001
    }
}
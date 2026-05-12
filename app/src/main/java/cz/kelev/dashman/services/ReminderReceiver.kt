package cz.kelev.dashman.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log
import cz.kelev.dashman.MainActivity
import cz.kelev.dashman.R
import cz.kelev.dashman.TtsManager
import cz.kelev.dashman.storage.DashmanDatabase
import cz.kelev.dashman.storage.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import cz.kelev.dashman.services.engine.ReminderEngine
import cz.kelev.dashman.storage.AppPrefs

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        Log.d(
            "ReminderReceiver",
            "onReceive start: action=${intent.action} extras=${intent.extras}"
        )

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        Log.d("ReminderReceiver", "onReceive reminderId=$reminderId")

        if (reminderId <= 0L) {
            Log.e("ReminderReceiver", "Invalid reminderId=$reminderId")
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ReminderRepository(
                    DashmanDatabase.get(appContext).reminderDao()
                )

                Log.d("ReminderReceiver", "Loading reminder from DB: id=$reminderId")
                val reminder = repo.getById(reminderId)

                if (reminder == null) {
                    Log.e("ReminderReceiver", "Reminder not found in DB: id=$reminderId")
                    return@launch
                }

                if (reminder.isDone) {
                    Log.w("ReminderReceiver", "Reminder skipped: id=$reminderId isDone=${reminder.isDone} status=${reminder.status}")
                    return@launch
                }

                if (reminder.status != "active") {
                    Log.w("ReminderReceiver", "Reminder already processed: id=$reminderId status=${reminder.status}")
                    return@launch
                }

                Log.d(
                    "ReminderReceiver",
                    "Reminder loaded: id=${reminder.id}, text=${reminder.text}, dueAt=${reminder.dueAt}, status=${reminder.status}, isDone=${reminder.isDone}"
                )

                ensureNotificationChannel(appContext)
                Log.d("ReminderReceiver", "Notification channel ensured")

                vibrate(appContext)
                Log.d("ReminderReceiver", "Vibration call finished")

                val engine = ReminderEngine(repo, appContext)
                engine.onReminderFired(reminderId)
                Log.d("ReminderReceiver", "onReminderFired() finished: id=$reminderId")

                val updated = repo.getById(reminderId)
                Log.d(
                    "ReminderReceiver",
                    "after fire: id=$reminderId status=${updated?.status} isDone=${updated?.isDone} dueAt=${updated?.dueAt}"
                )

                showNotification(appContext, reminder.id, reminder.text)
                Log.d("ReminderReceiver", "showNotification() finished")

                // TTS в фоне намеренно отключён — конфиденциальность.
                // Проговор происходит только когда пользователь открывает
                // приложение через тап на уведомление (handleReminderIntent в MainActivity)
                Log.d("ReminderReceiver", "TTS skipped: background speaking disabled")
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "onReceive failed", e)
            } finally {
                Log.d("ReminderReceiver", "onReceive finish: reminderId=$reminderId")
                pendingResult.finish()
            }
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминания Dashman",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Срабатывание напоминаний"
            enableVibration(true)
        }

        manager.createNotificationChannel(channel)
    }

    private fun showNotification(context: Context, reminderId: Long, reminderText: String) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Напоминание")
            .setContentText(reminderText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminderText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            Log.d("ReminderReceiver", "POST_NOTIFICATIONS granted=$granted")

            if (!granted) {
                Log.e("ReminderReceiver", "Notification blocked: POST_NOTIFICATIONS not granted")
                return
            }
        }

        NotificationManagerCompat.from(context).notify(reminderId.toInt(), notification)
        Log.d("ReminderReceiver", "Notification shown: id=$reminderId")
    }

    private fun vibrate(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 300, 150, 300, 150, 500),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 300, 150, 300, 150, 500),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 300, 150, 300, 150, 500), -1)
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "Vibration failed", e)
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        private const val CHANNEL_ID = "dashman_reminders"
    }
}
package cz.kelev.dashman.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cz.kelev.dashman.services.engine.ReminderEngine
import cz.kelev.dashman.storage.DashmanDatabase
import cz.kelev.dashman.storage.ReminderRepository
import cz.kelev.dashman.storage.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val repo = ReminderRepository(DashmanDatabase.get(appContext).reminderDao())
                val engine = ReminderEngine(repo, appContext)
                engine.rescheduleAll()
                Log.d("ReminderRestoreReceiver", "Reschedule complete: action=${intent.action}")

                // Восстанавливаем брифинг если он был включён
                val prefs = AppPrefs(appContext)
                if (prefs.getBriefingEnabled()) {
                    val (h, m) = prefs.getBriefingTime()
                    BriefingScheduler.schedule(appContext, h, m)
                    Log.d("ReminderRestoreReceiver", "Briefing rescheduled at $h:$m")
                }
            } catch (e: Exception) {
                Log.e("ReminderRestoreReceiver", "Reschedule failed", e)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
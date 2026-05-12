package cz.kelev.dashman.services

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class HotwordServiceStarter(
    private val context: Context
) {
    fun start() {
        val intent = Intent(context, HotwordService::class.java).apply {
            action = HotwordService.ACTION_START_WAKEWORD
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        val intent = Intent(context, HotwordService::class.java).apply {
            action = HotwordService.ACTION_STOP_WAKEWORD
        }
        context.startService(intent)
    }
}
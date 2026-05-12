package cz.kelev.dashman

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.MutableState
import androidx.core.content.ContextCompat

class HotwordController(
    private val activity: ComponentActivity,
    private val requestAudioPermission: ActivityResultLauncher<String>,
    private val requestNotifPermission: ActivityResultLauncher<String>,
    private val hotwordEnabledState: MutableState<Boolean>,
    private val startHotwordService: () -> Unit,
    private val stopHotwordService: () -> Unit,
) {
    private var enableAfterAudioPermission: Boolean = false
    private var enableAfterNotifPermission: Boolean = false

    fun onToggle(enabled: Boolean) {
        if (enabled) {
            // 1) Уведомления (Android 13+)
            if (Build.VERSION.SDK_INT >= 33) {
                val notifGranted = ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (!notifGranted) {
                    enableAfterNotifPermission = true
                    requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }

            // 2) Микрофон
            val audioGranted = activity.hasRecordAudioPermission()
            if (!audioGranted) {
                enableAfterAudioPermission = true
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                return
            }

            hotwordEnabledState.value = true
            startHotwordService()
        } else {
            hotwordEnabledState.value = false
            stopHotwordService()
        }
    }

    fun onAudioPermissionResult(granted: Boolean) {
        if (!granted) return
        if (!enableAfterAudioPermission) return

        enableAfterAudioPermission = false
        hotwordEnabledState.value = true

        // Небольшая задержка: некоторые устройства тупят с аудиостеком после GrantPermissionsActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startHotwordService()
        }, 1200)
    }

    fun onNotifPermissionResult(granted: Boolean) {
        if (!granted) return
        if (!enableAfterNotifPermission) return

        enableAfterNotifPermission = false

        // После уведомлений всё равно проверяем микрофон
        val audioGranted = activity.hasRecordAudioPermission()
        if (!audioGranted) {
            enableAfterAudioPermission = true
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        hotwordEnabledState.value = true
        startHotwordService()
    }
}
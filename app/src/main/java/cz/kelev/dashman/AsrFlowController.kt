package cz.kelev.dashman

import android.Manifest
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher

class AsrFlowController(
    private val requestAudioPermission: ActivityResultLauncher<String>,
    private val hasAudioPermission: () -> Boolean,
    private val startAsr: () -> Unit
) {
    private var startAfterPermission: Boolean = false
    private var startAsrNow: Boolean = false

    fun onMicClick() {
        if (!hasAudioPermission()) {
            startAfterPermission = true
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startAsr()
        }
    }

    fun onAudioPermissionResult(granted: Boolean) {
        if (!granted) return
        if (!(startAfterPermission || startAsrNow)) return

        startAfterPermission = false
        startAsrNow = false
        startAsr()
    }

    fun onNewIntent(intent: Intent?) {
        val shouldStart = intent?.getBooleanExtra(MainActivity.EXTRA_START_ASR, false) == true
        if (!shouldStart) return

        if (!hasAudioPermission()) {
            startAsrNow = true
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startAsr()
        }
    }
}
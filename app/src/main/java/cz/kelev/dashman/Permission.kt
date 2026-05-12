package cz.kelev.dashman

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

fun Context.hasRecordAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

fun ComponentActivity.registerRecordAudioPermissionLauncher(
    onResult: (Boolean) -> Unit
): ActivityResultLauncher<String> =
    registerForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
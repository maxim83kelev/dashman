package cz.kelev.dashman

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

class PermissionCoordinator(private val activity: Activity) {

    private val prefs: SharedPreferences by lazy {
        activity.getSharedPreferences("dashman_permissions", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_FIRST_LAUNCH_SETTINGS_SHOWN = "first_launch_settings_shown"
    }

    fun runFirstLaunchFlowIfNeeded() {
        val alreadyShown = prefs.getBoolean(KEY_FIRST_LAUNCH_SETTINGS_SHOWN, false)
        Log.d("DashmanPerm", "runFirstLaunchFlowIfNeeded: alreadyShown=$alreadyShown")

        if (alreadyShown) return

        openAppSettingsIfNeeded()
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_SETTINGS_SHOWN, true).apply()

        Log.d("DashmanPerm", "First-launch settings screen opened")
    }

    fun hasExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

        val alarmManager =
            activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        return alarmManager.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager =
                activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {
                Log.d("DashmanPerm", "Opening exact alarm settings")
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                activity.startActivity(intent)
            }
        }
    }

    fun openBatteryOptimizationSettings() {
        val pm =
            activity.getSystemService(Context.POWER_SERVICE) as PowerManager

        if (!pm.isIgnoringBatteryOptimizations(activity.packageName)) {
            Log.d("DashmanPerm", "Opening battery optimization settings")
            val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        }
    }

    private fun openAppSettingsIfNeeded() {
        Log.d("DashmanPerm", "Opening app settings page")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${activity.packageName}")
        activity.startActivity(intent)
    }
}
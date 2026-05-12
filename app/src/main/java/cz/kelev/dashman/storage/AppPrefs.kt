package cz.kelev.dashman.storage

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(private val context: Context) {

    private val prefs: SharedPreferences =
    context.getSharedPreferences("dashman_prefs", Context.MODE_PRIVATE)

    fun setCleanupMode(mode: Int) {
        prefs.edit().putInt("cleanup_mode", mode).apply()
    }

    fun getCleanupMode(): Int {
        return prefs.getInt("cleanup_mode", 0)
    }

    fun setLastCleanupTime(time: Long) {
        prefs.edit().putLong("last_cleanup_time", time).apply()
    }

    fun getLastCleanupTime(): Long {
        return prefs.getLong("last_cleanup_time", 0L)
    }

    // ─── Брифинг ──────────────────────────────────────────────────────────

    fun setBriefingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("briefing_enabled", enabled).apply()
    }

    fun getBriefingEnabled(): Boolean {
        return prefs.getBoolean("briefing_enabled", false)
    }

    /** Сохраняет время как "HH:MM" */
    fun setBriefingTime(hour: Int, minute: Int) {
        prefs.edit().putInt("briefing_hour", hour).putInt("briefing_minute", minute).apply()
    }

    /** Возвращает Pair(hour, minute), по умолчанию 9:00 */
    fun getBriefingTime(): Pair<Int, Int> {
        val h = prefs.getInt("briefing_hour", 9)
        val m = prefs.getInt("briefing_minute", 0)
        return Pair(h, m)
    }

    // ─── Озвучивание напоминаний ──────────────────────────────────────────

    fun setSpeakOnFire(enabled: Boolean) {
        prefs.edit().putBoolean("speak_on_fire", enabled).apply()
    }

    fun getSpeakOnFire(): Boolean {
        return prefs.getBoolean("speak_on_fire", true) // по умолчанию включено
    }

    // ─── Лицензия / Device ID ─────────────────────────────────────────────

    fun getDeviceId(): String {
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    fun setLicenseStatus(status: String) {
        prefs.edit().putString("license_status", status).apply()
    }

    fun getLicenseStatus(): String {
        return prefs.getString("license_status", "unknown") ?: "unknown"
    }

    // ─── Срок лицензии ────────────────────────────────────────────────────────

    fun setLicenseExpiry(expiresAt: Long) {
        prefs.edit().putLong("license_expires_at", expiresAt).apply()
    }

    fun getLicenseExpiry(): Long {
        return prefs.getLong("license_expires_at", 0L)
    }

    // ─── Резервное копирование ────────────────────────────────────────────

    fun setLastBackupTime(time: Long) {
        prefs.edit().putLong("last_backup_time", time).apply()
    }

    fun getLastBackupTime(): Long {
        return prefs.getLong("last_backup_time", 0L)
    }

    // ─── Trial период ─────────────────────────────────────────────────────────

    fun getInstallDate(): Long {
        var ts = prefs.getLong("install_date", 0L)
        if (ts == 0L) {
            ts = System.currentTimeMillis() / 1000L
            prefs.edit().putLong("install_date", ts).apply()
        }
        return ts
    }

    // ─── Голосовые диалоги (лимит freemium) ──────────────────────────────────

    fun getVoiceDialogCount(): Int {
        resetVoiceCountIfNewMonth()
        return prefs.getInt("voice_dialog_count", 0)
    }

    fun setVoiceDialogCount(count: Int) {
        val now = java.util.Calendar.getInstance()
        prefs.edit()
            .putInt("voice_dialog_count", count)
            .putInt("voice_dialog_month", now.get(java.util.Calendar.MONTH))
            .putInt("voice_dialog_year", now.get(java.util.Calendar.YEAR))
            .apply()
    }

    fun incrementVoiceDialogCount() {
        resetVoiceCountIfNewMonth()
        val current = prefs.getInt("voice_dialog_count", 0)
        prefs.edit().putInt("voice_dialog_count", current + 1).apply()
    }

    fun isVoiceLimitReached(limit: Int = 20): Boolean =
        getVoiceDialogCount() >= limit

    private fun resetVoiceCountIfNewMonth() {
        val now = java.util.Calendar.getInstance()
        val storedMonth = prefs.getInt("voice_dialog_month", -1)
        val storedYear  = prefs.getInt("voice_dialog_year", -1)
        if (storedMonth != now.get(java.util.Calendar.MONTH) ||
            storedYear  != now.get(java.util.Calendar.YEAR)) {
            prefs.edit()
                .putInt("voice_dialog_count", 0)
                .putInt("voice_dialog_month", now.get(java.util.Calendar.MONTH))
                .putInt("voice_dialog_year",  now.get(java.util.Calendar.YEAR))
                .apply()
        }
    }

    // ─── Обновления ──────────────────────────────────────────────────────────

    fun getUpdateOfferedVersion(): String =
        prefs.getString("update_offered_version", "") ?: ""

    fun setUpdateOfferedVersion(version: String) =
        prefs.edit().putString("update_offered_version", version).apply()

    // ─── Первый запуск ───────────────────────────────────────────────────────

    fun isFirstLaunch(): Boolean =
        prefs.getBoolean("is_first_launch", true)

    fun markFirstLaunchDone() =
        prefs.edit().putBoolean("is_first_launch", false).apply()

}
package cz.kelev.dashman.network

import android.content.Context
import cz.kelev.dashman.storage.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LicenseChecker {

    suspend fun check(context: Context): LicenseResult {
        val prefs = AppPrefs(context)
        val deviceId = prefs.getDeviceId()
        val result = DashmanApiClient.checkLicense(deviceId)
        // Сохраняем статус локально
        prefs.setLicenseStatus(when (result) {
            is LicenseResult.Active       -> { prefs.setLicenseExpiry(result.expiresAt); "active" }
            is LicenseResult.Revoked      -> "revoked"
            is LicenseResult.Expired      -> "expired"
            is LicenseResult.NotFound     -> "not_found"
            is LicenseResult.NetworkError -> "network_error"
        })
        return result

    }
}
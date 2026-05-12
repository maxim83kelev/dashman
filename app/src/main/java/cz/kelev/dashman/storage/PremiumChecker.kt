package cz.kelev.dashman.storage

class PremiumChecker(private val prefs: AppPrefs) {

    val isPremium: Boolean
        get() {
            // Активная лицензия
            if (prefs.getLicenseStatus() == "active") {
                val expiry = prefs.getLicenseExpiry()
                if (expiry <= 0L) return true // бессрочная
                if (System.currentTimeMillis() / 1000L < expiry) return true
            }
            // Trial 30 дней с момента установки
            val installDate = prefs.getInstallDate()
            val trialExpiry = installDate + 30L * 86400L
            return System.currentTimeMillis() / 1000L < trialExpiry
        }

    val isTrialActive: Boolean
        get() {
            if (prefs.getLicenseStatus() == "active") return false
            val installDate = prefs.getInstallDate()
            val trialExpiry = installDate + 30L * 86400L
            return System.currentTimeMillis() / 1000L < trialExpiry
        }

    val trialDaysLeft: Int
        get() {
            val installDate = prefs.getInstallDate()
            val trialExpiry = installDate + 30L * 86400L
            val now = System.currentTimeMillis() / 1000L
            return maxOf(0, ((trialExpiry - now) / 86400L).toInt())
        }
}
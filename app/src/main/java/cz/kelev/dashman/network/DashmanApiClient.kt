package cz.kelev.dashman.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import cz.kelev.dashman.BuildConfig


object DashmanApiClient {

    private const val BASE_URL = BuildConfig.BASE_URL

    // ─── Проверка лицензии ────────────────────────────────────────────────

    suspend fun checkLicense(deviceId: String): LicenseResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("app", "dashman")
                put("device", deviceId)
            }
            val response = post("$BASE_URL/license/check", body.toString())
            val json = JSONObject(response)
            val bodyB64 = json.optString("body_b64", "")
            if (bodyB64.isEmpty()) return@withContext LicenseResult.NotFound

            val decoded = String(android.util.Base64.decode(bodyB64, android.util.Base64.DEFAULT))
            val payload = JSONObject(decoded)
            val revoked = payload.optBoolean("revoked", true)
            val exp = payload.optLong("exp", 0L)

            if (revoked) return@withContext LicenseResult.Revoked
            if (exp > 0L && System.currentTimeMillis() / 1000 > exp) return@withContext LicenseResult.Expired
            LicenseResult.Active(expiresAt = exp)

        } catch (e: Exception) {
            LicenseResult.NetworkError
        }
    }

    // ─── Связь с автором ──────────────────────────────────────────────────

    suspend fun sendFeedback(
        deviceId: String,
        message: String,
        logText: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("app", "dashman")
                put("device_id", deviceId)
                put("level", "feedback")
                put("message", message)
                if (!logText.isNullOrBlank()) put("log", logText.take(3000))
            }
            post("$BASE_URL/app_error", body.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun requestAccess(
        deviceId: String,
        telegram: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("app", "dashman")
                put("device_id", deviceId)
                put("level", "access_request")
                put("message", "Запрос доступа${if (!telegram.isNullOrBlank()) " от $telegram" else ""}")
                put("version", BuildConfig.VERSION_NAME)
            }
            post("$BASE_URL/app_error", body.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    // ─── HTTP POST ────────────────────────────────────────────────────────

    private fun post(url: String, jsonBody: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }
        return conn.inputStream.bufferedReader().readText()
    }

    // ─── Голосовые диалоги (freemium счётчик) ────────────────────────────────

    suspend fun getVoiceUsage(deviceId: String): Int = withContext(Dispatchers.IO) {
        try {
            val response = get("$BASE_URL/voice_usage/$deviceId")
            val json = JSONObject(response)
            json.optInt("count", 0)
        } catch (e: Exception) {
            -1 // офлайн — не блокируем, доверяем локальному счётчику
        }
    }

    suspend fun incrementVoiceUsage(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("app", "dashman")
            }
            val response = post("$BASE_URL/voice_usage/$deviceId/increment", body.toString())
            val json = JSONObject(response)
            json.optBoolean("limit_reached", false)
        } catch (e: Exception) {
            false // офлайн — разрешаем
        }
    }

    // ─── HTTP GET ─────────────────────────────────────────────────────────────

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        return conn.inputStream.bufferedReader().readText()
    }
}

// ─── Результат проверки лицензии ──────────────────────────────────────────

sealed class LicenseResult {
    data class Active(val expiresAt: Long = 0L) : LicenseResult()
    object Revoked      : LicenseResult()
    object Expired      : LicenseResult()
    object NotFound     : LicenseResult()
    object NetworkError : LicenseResult()
}
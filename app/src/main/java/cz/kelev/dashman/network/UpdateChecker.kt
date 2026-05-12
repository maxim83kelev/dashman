package cz.kelev.dashman.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String
)

sealed class UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult()
    object UpToDate : UpdateResult()
    object NetworkError : UpdateResult()
}

object UpdateChecker {

    private const val GITHUB_OWNER = "maxim83kelev"
    private const val GITHUB_REPO  = "dashman"
    private const val API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    suspend fun check(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val body    = json.optString("body", "")

            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            if (tagName.isEmpty() || apkUrl.isEmpty()) return@withContext UpdateResult.NetworkError

            if (isNewer(tagName, currentVersion(context))) {
                UpdateResult.Available(UpdateInfo(tagName, body, apkUrl))
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.NetworkError
        }
    }

    private fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map  { it.toIntOrNull() ?: 0 }
        val size = maxOf(r.size, l.size)
        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}
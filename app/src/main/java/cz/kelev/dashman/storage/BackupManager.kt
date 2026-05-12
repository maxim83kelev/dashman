package cz.kelev.dashman.storage

import android.content.Context
import android.net.Uri
import cz.kelev.dashman.storage.ReminderEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val reminders: List<ReminderEntity>
)

class BackupManager(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    companion object {
        const val CURRENT_VERSION = 1
    }

    fun exportToUri(uri: Uri, reminders: List<ReminderEntity>): Result<Unit> {
        return try {
            val backup = BackupData(reminders = reminders)
            val jsonString = json.encodeToString(backup)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(jsonString.toByteArray(Charsets.UTF_8))
            } ?: return Result.failure(Exception("Не удалось открыть файл"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importFromUri(uri: Uri): Result<BackupData> {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: return Result.failure(Exception("Не удалось прочитать файл"))
            val backup = json.decodeFromString<BackupData>(text)
            if (backup.version > CURRENT_VERSION) {
                return Result.failure(Exception("VERSION_MISMATCH"))
            }
            Result.success(backup)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
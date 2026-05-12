package cz.kelev.dashman

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object DashmanLogger {

    private const val LOG_FILE = "dashman.log"
    private const val MAX_SIZE_BYTES = 512 * 1024L // 512 КБ — потом обрезаем

    private var logFile: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE)
    }

    fun d(tag: String, msg: String) {
        android.util.Log.d(tag, msg)
        write("D", tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        android.util.Log.e(tag, msg, t)
        val full = if (t != null) "$msg | ${t.javaClass.simpleName}: ${t.message}" else msg
        write("E", tag, full)
    }

    fun w(tag: String, msg: String) {
        android.util.Log.w(tag, msg)
        write("W", tag, msg)
    }

    private fun write(level: String, tag: String, msg: String) {
        val f = logFile ?: return
        try {
            // Обрезаем если файл вырос
            if (f.exists() && f.length() > MAX_SIZE_BYTES) {
                val lines = f.readLines().takeLast(500)
                f.writeText(lines.joinToString("\n") + "\n")
            }
            val line = "${fmt.format(Date())} $level/$tag: $msg\n"
            f.appendText(line)
        } catch (_: Exception) {}
    }

    fun readLast(lines: Int = 100): String {
        val f = logFile ?: return "Лог недоступен"
        if (!f.exists()) return "Лог пуст"
        return f.readLines().takeLast(lines).joinToString("\n")
    }

    fun getFile(): File? = logFile?.takeIf { it.exists() }
}
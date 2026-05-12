package cz.kelev.dashman.services

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.R as PorcupineR
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cz.kelev.dashman.MainActivity
import cz.kelev.dashman.R
import java.io.File
import java.io.FileOutputStream
import android.content.pm.ServiceInfo
import android.util.Log
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean


class HotwordService : Service() {

    private var porcupine: Porcupine? = null
    private var audioThread: Thread? = null
    private var recorder: AudioRecord? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        // Поднимаем FGS, но НЕ строим Porcupine сами.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                buildNotification("Hotword готов. Жду команду START…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(
                NOTIF_ID,
                buildNotification("Hotword готов. Жду команду START…")
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WAKEWORD -> {
                Log.i("HotwordService", "ACTION_START_WAKEWORD")
                Thread { startWakeWord() }.start()
            }
            ACTION_STOP_WAKEWORD -> {
                Log.i("HotwordService", "ACTION_STOP_WAKEWORD")
                stopWakeWord()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopWakeWord()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dashman Hotword",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
    }

    private fun startWakeWord() {
        val accessKey = "G9+Z8GoO3BDAECLPKpP8spefgFbKegr5rLnxSDEwz8XsdWvkEtwZdw=="

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification("⚙ startWakeWord() entered"))

        try {
            // Porcupine не читает keyword из assets напрямую, нужен путь к файлу
            val keywordPath = prepareKeywordFile("hey-dash-man_en_android_v4_0_0.ppn")
            nm.notify(NOTIF_ID, buildNotification("📦 keywordPath: $keywordPath"))

            val modelPath = copyRawToFile(
                rawId = PorcupineR.raw.porcupine_params,
                outName = "porcupine_params.pv"
            )

            val pvSize = File(modelPath).length()
                nm.notify(NOTIF_ID, buildNotification("📦 modelPath: $modelPath"))
                nm.notify(NOTIF_ID, buildNotification("📦 modelSize: ${pvSize} bytes"))

            if (pvSize < 10_000L) {
                throw IllegalStateException("porcupine_params.pv подозрительно маленький: $pvSize bytes. Значит копирование/ресурс не тот.")
            }

            // ЭТАП 1: Porcupine build
            nm.notify(NOTIF_ID, buildNotification("🧠 Porcupine: building..."))

            val startedAt = SystemClock.elapsedRealtime()

            var builtPorcupine: Porcupine? = null
            var buildError: Throwable? = null

            val building = AtomicBoolean(true)

            // watchdog: если процесс жив, раз в секунду обновляем уведомление
            val watchdog = Thread {
                while (building.get()) {
                    val dt = SystemClock.elapsedRealtime() - startedAt
                    nm.notify(NOTIF_ID, buildNotification("🧠 Porcupine: building... ${dt}ms"))
                    try { Thread.sleep(1000) } catch (_: Throwable) {}
                }
            }.apply { start() }

            val buildThread = Thread {
                try {
                    builtPorcupine = Porcupine.Builder()
                        .setAccessKey(accessKey)
                        .setKeywordPath(keywordPath)
                        .setModelPath(modelPath)
                        .build(this@HotwordService)
                } catch (t: Throwable) {
                    buildError = t
                }
            }.apply { start() }

            // ждём максимум 30 секунд (на первом старте/на некоторых девайсах init реально дольше)
            buildThread.join(30_000)

            if (buildThread.isAlive) {
                building.set(false)
                try { watchdog.join(200) } catch (_: Throwable) {}
                val dt = SystemClock.elapsedRealtime() - startedAt
                nm.notify(NOTIF_ID, buildNotification("❌ Porcupine build всё ещё висит (${dt}ms)"))
                Log.e("HotwordService", "Porcupine build STILL HANG after ${dt}ms")
                // дальше оставляем как было: дамп стеков и стоп
                stopWakeWord()
                stopSelf()
                return
            }

            buildError?.let {
                building.set(false) 
                try { watchdog.join(200) } catch (_: Throwable) {}

                val msg = it.message ?: it.javaClass.simpleName
                nm.notify(NOTIF_ID, buildNotification("❌ Porcupine build failed: $msg"))
                Log.e("HotwordService", "Porcupine build failed", it)
                throw it
            }

            building.set(false) // ⬅️ ВАЖНО
            try { watchdog.join(200) } catch (_: Throwable) {}

            val p = builtPorcupine ?: throw IllegalStateException("Porcupine build returned null")

            porcupine = p
            nm.notify(NOTIF_ID, buildNotification("✅ Porcupine: built"))

            // ЭТАП 2: AudioRecord init + start
            nm.notify(NOTIF_ID, buildNotification("🎙 AudioRecord: init..."))

            val bufferSize = AudioRecord.getMinBufferSize(
                p.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(p.frameLength * 2)

            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                p.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            val r = recorder ?: throw IllegalStateException("AudioRecord init failed")

            nm.notify(NOTIF_ID, buildNotification("🎙 AudioRecord: startRecording..."))
            r.startRecording()

            nm.notify(
                NOTIF_ID,
                buildNotification("🎙 state=${r.state}, rec=${r.recordingState}")
            )

            if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                nm.notify(NOTIF_ID, buildNotification("AudioRecord not recording"))
                throw IllegalStateException("AudioRecord not recording")
            }

            audioThread = Thread {
                val frame = ShortArray(p.frameLength)
                try {
                    while (!Thread.currentThread().isInterrupted) {

                        // Добираем фрейм полностью (AudioRecord часто читает кусками)
                        var offset = 0
                        while (offset < frame.size && !Thread.currentThread().isInterrupted) {
                            val n = r.read(frame, offset, frame.size - offset)

                            // Ошибки AudioRecord: выходим и покажем причину
                            if (n < 0) {
                                nm.notify(NOTIF_ID, buildNotification("AudioRecord read error: $n"))
                                return@Thread
                            }
                            offset += n
                        }
                        if (offset == frame.size) {
                            val keywordIndex = p.process(frame)
                            if (keywordIndex >= 0) {
                                onHotword()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    val msg = t.message ?: t.javaClass.simpleName
                    nm.notify(NOTIF_ID, buildNotification("Hotword thread error: $msg"))
                    Log.e("HotwordService", "audioThread crashed", t)
                }
            }.apply { start() }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification("Hotword включён: Say 'Hey Dashman'"))

        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            nm.notify(
                NOTIF_ID,
                buildNotification("❌ Hotword НЕ запустился: $msg")
            )
            Log.e("HotwordService", "startWakeWord failed", t)

            stopWakeWord()
            stopSelf()
        }
    }

    private fun prepareKeywordFile(assetName: String): String {
        val outFile = File(cacheDir, assetName)
        // Всегда перезаписываем, чтобы не залипать на старом/битом .ppn из кэша
        if (outFile.exists()) outFile.delete()
        try {
            assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Throwable) {
            throw RuntimeException(
                "Keyword asset not found: $assetName. Проверь app/src/main/assets и имя файла.",
                e
            )
        }
        return outFile.absolutePath
    }

    private fun copyRawToFile(@androidx.annotation.RawRes rawId: Int, outName: String): String {
        val outFile = File(cacheDir, outName)

        // Всегда перезаписываем (важно, если раньше создался 0-byte файл)
        if (outFile.exists()) outFile.delete()

        resources.openRawResource(rawId).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
                output.fd.sync() // важно: принудительно дописываем на диск
            }
        }

        val size = outFile.length()
        Log.i("HotwordService", "PV extracted: ${outFile.absolutePath}, size=$size")

        if (size < 10_000L) {
            throw IllegalStateException("PV файл слишком маленький ($size bytes). rawId=$rawId, outName=$outName")
        }

return outFile.absolutePath
    }

    private fun stopWakeWord() {
        val t = audioThread
        val r = recorder
        audioThread = null
        recorder = null

        try { t?.interrupt() } catch (_: Throwable) {}
        try { t?.join(500) } catch (_: Throwable) {} // чтобы поток успел выйти из read()

        try { r?.stop() } catch (_: Throwable) {}
        try { r?.release() } catch (_: Throwable) {}

        try { porcupine?.delete() } catch (_: Throwable) {}
        porcupine = null
    }

    private fun onHotword() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification("🔥 Hey Dashman DETECTED"))

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_START_ASR, true)
        }

        try {
            startActivity(intent)
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            Log.e("HotwordService", "startActivity blocked", t)
            nm.notify(NOTIF_ID, buildNotification("⚠ Activity blocked: $msg"))
            return
        }

        nm.notify(NOTIF_ID, buildNotification("HeyDashman → открываю и включаю микрофон"))
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dashman")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "dashman_hotword"
        private const val NOTIF_ID = 2001
        const val EXTRA_START_ASR = "EXTRA_START_ASR"

        const val ACTION_START_WAKEWORD = "cz.kelev.dashman.action.START_WAKEWORD"
        const val ACTION_STOP_WAKEWORD  = "cz.kelev.dashman.action.STOP_WAKEWORD"
    }
}
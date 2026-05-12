package cz.kelev.dashman

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var onDoneCallback: (() -> Unit)? = null

    // Очередь фраз которые ждут пока TTS инициализируется
    private data class PendingUtterance(
        val text: String,
        val utteranceId: String,
        val onDone: (() -> Unit)?
    )
    private val pendingQueue = mutableListOf<PendingUtterance>()

    init {
        initTts()
    }

    private fun initTts() {
        isReady = false
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    tts?.language = Locale("ru", "RU")
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            onDoneCallback?.invoke()
                            onDoneCallback = null
                        }
                        override fun onError(utteranceId: String?) {
                            Log.w("TtsManager", "TTS error for utteranceId=$utteranceId")
                            onDoneCallback?.invoke()
                            onDoneCallback = null
                        }
                    })
                    isReady = true
                    Log.d("TtsManager", "TTS initialized successfully")
                    flushPendingQueue()
                } catch (e: Throwable) {
                    Log.e("TtsManager", "TTS init failed", e)
                }
            } else {
                Log.e("TtsManager", "TTS init status=$status, retrying in 1s")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    initTts()
                }, 1000L)
            }
        }
    }

    private fun flushPendingQueue() {
        synchronized(pendingQueue) {
            val items = pendingQueue.toList()
            pendingQueue.clear()
            items.forEach { item ->
                if (item.onDone != null) onDoneCallback = item.onDone
                tts?.speak(item.text, TextToSpeech.QUEUE_ADD, null, item.utteranceId)
                Log.d("TtsManager", "Flushed pending: ${item.text}")
            }
        }
    }

    fun speakNow(text: String, utteranceId: String = "dashman_tts") {
        if (!isReady || tts == null) {
            Log.w("TtsManager", "TTS not ready, queuing: $text")
            synchronized(pendingQueue) {
                pendingQueue.clear() // сбрасываем старые — нужна только последняя фраза
                pendingQueue.add(PendingUtterance(text, utteranceId, null))
            }
            if (tts == null) initTts()
            return
        }
        try {
            onDoneCallback = null
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            Log.d("TtsManager", "Speaking: $text")
        } catch (e: Throwable) {
            Log.e("TtsManager", "speakNow failed, reinit", e)
            isReady = false
            synchronized(pendingQueue) {
                pendingQueue.clear()
                pendingQueue.add(PendingUtterance(text, utteranceId, null))
            }
            initTts()
        }
    }

    fun speakThenDo(text: String, onDone: () -> Unit) {
        if (!isReady || tts == null) {
            Log.w("TtsManager", "TTS not ready, queuing with callback: $text")
            synchronized(pendingQueue) {
                pendingQueue.clear()
                pendingQueue.add(PendingUtterance(text, "dashman_tts", onDone))
            }
            if (tts == null) initTts()
            return
        }
        try {
            onDoneCallback = onDone
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dashman_tts")
            Log.d("TtsManager", "Speaking with callback: $text")
        } catch (e: Throwable) {
            Log.e("TtsManager", "speakThenDo failed, reinit", e)
            isReady = false
            synchronized(pendingQueue) {
                pendingQueue.clear()
                pendingQueue.add(PendingUtterance(text, "dashman_tts", onDone))
            }
            initTts()
        }
    }

    fun shutdown() {
        try {
            isReady = false
            onDoneCallback = null
            synchronized(pendingQueue) { pendingQueue.clear() }
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {
        } finally {
            tts = null
        }
    }
}
package cz.kelev.dashman.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class AsrController(
    private val context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var recognizer: SpeechRecognizer? = null

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Распознавание речи недоступно на устройстве")
            return null
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onListeningChanged(true)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        onListeningChanged(false)

                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Ничего не расслышал"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ты молчал слишком долго"
                            SpeechRecognizer.ERROR_AUDIO -> "Проблема с микрофоном"
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Проблема с сетью"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознавание уже занято"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на микрофон"
                            else -> "Ошибка распознавания речи: $error"
                        }

                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        onListeningChanged(false)
                        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = list?.firstOrNull()?.trim().orEmpty()

                        if (text.isBlank()) {
                            onError("Ничего не расслышал")
                        } else {
                            onResult(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
        return recognizer
    }

    fun start() {
        val r = ensureRecognizer() ?: return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
        r.startListening(intent)
    }

    fun restart() {
        stop()
        start()
    }

    fun stop() {
        recognizer?.stopListening()
        onListeningChanged(false)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
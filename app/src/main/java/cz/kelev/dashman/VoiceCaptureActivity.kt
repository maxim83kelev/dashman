package cz.kelev.dashman

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import cz.kelev.dashman.services.AsrController
import cz.kelev.dashman.services.voice.delete.VoiceDeleteFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VoiceCaptureActivity : ComponentActivity() {

    private lateinit var asr: AsrController
    private lateinit var voiceDeleteFlow: VoiceDeleteFlow
    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val graph = AppGraph(this)
        val brain = graph.brain

        // Прогреваем reminders — иначе StateFlow вернёт emptyList() при первом обращении
        warmupScope.launch {
            brain.reminders.collect { }
        }

        voiceDeleteFlow = VoiceDeleteFlow(
            brain = brain,
            matcher = { reminder, query ->
                reminder.text.lowercase().contains(query.lowercase())
            },
            labelProvider = { reminder ->
                reminder.text
            },
            say = { phrase ->
                android.util.Log.d("Dashman", phrase)
                graph.ttsManager.speakNow(phrase) // озвучиваем вопрос пользователю
            },
            showDeleteCandidates = { matches ->
                android.util.Log.d("Dashman", "Matches: ${matches.size}")
            }
        )

        asr = AsrController(
            context = this,
            onListeningChanged = { },
            onError = {},
            onResult = onResult@{ text ->
                if (text.isBlank()) {
                    finish()
                } else {
                    val handled = voiceDeleteFlow.handle(text)

                    if (handled) {
                        if (voiceDeleteFlow.isBusy) {
                            asr.restart()
                        } else {
                            finish()
                        }
                        return@onResult
                    } else if (Regex("""\bудал""").containsMatchIn(text)) {
                        finish()
                        return@onResult
                    }

                    brain.addTextReminder(text)
                    finish()
                }
            }
        )

        // Даём 300мс на загрузку напоминаний из базы перед стартом ASR
        Handler(Looper.getMainLooper()).postDelayed({
            asr.start()
        }, 300L)
    }

    override fun onDestroy() {
        warmupScope.cancel()
        asr.destroy()
        super.onDestroy()
    }
}

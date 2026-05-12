package cz.kelev.dashman

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cz.kelev.dashman.services.brain.BrainContract
import cz.kelev.dashman.services.brain.SimpleBrain
import cz.kelev.dashman.services.engine.ReminderEngine
import cz.kelev.dashman.services.engine.ReminderEngineContract
import cz.kelev.dashman.storage.DashmanDatabase
import cz.kelev.dashman.storage.ReminderRepository
import cz.kelev.dashman.ui.theme.MainViewModel
import cz.kelev.dashman.storage.AppPrefs
import cz.kelev.dashman.TtsManager
import cz.kelev.dashman.network.DashmanApiClient
import cz.kelev.dashman.storage.PremiumChecker

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: AppPrefs by lazy { AppPrefs(appContext) }
    val premiumChecker: PremiumChecker by lazy { PremiumChecker(prefs) }

    val repo: ReminderRepository by lazy {
        ReminderRepository(DashmanDatabase.get(appContext).reminderDao())
    }

    val engine: ReminderEngineContract by lazy {
    ReminderEngine(repo, appContext)
    }

    val ttsManager: TtsManager by lazy {
        TtsManager(appContext)
    }

    val brain: BrainContract by lazy {
        SimpleBrain(
            repo = repo,
            engine = engine,
            say = { text ->
                ttsManager.speakNow(text)
            },
            prefs = prefs
        )
    }

    val apiClient get() = DashmanApiClient

    fun mainViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(brain, appContext) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
            }
        }
}
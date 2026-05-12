package cz.kelev.dashman.services.brain

import android.util.Log
import cz.kelev.dashman.FilterAnswerPhrases
import cz.kelev.dashman.GreetingPhrases
import cz.kelev.dashman.services.filter.ReminderFilter
import cz.kelev.dashman.services.engine.CreateResult
import cz.kelev.dashman.services.engine.ReminderEngineContract
import cz.kelev.dashman.storage.ReminderEntity
import cz.kelev.dashman.storage.ReminderRepository
import cz.kelev.dashman.storage.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import cz.kelev.dashman.storage.ReminderPriority

class SimpleBrain(
    private val repo: ReminderRepository,
    private val engine: ReminderEngineContract,
    private val say: ((String) -> Unit)? = null,
    private val prefs: AppPrefs
) : BrainContract {

    private val premiumChecker = cz.kelev.dashman.storage.PremiumChecker(prefs)

    private fun reminderCreatedPhrase(reminder: ReminderEntity): String {
        return when (reminder.priority) {
            ReminderPriority.CRITICAL -> GreetingPhrases.randomReminderCreatedCritical()
            ReminderPriority.IMPORTANT -> GreetingPhrases.randomReminderCreatedImportant()
            else -> GreetingPhrases.randomReminderCreated()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var cleanupMode: Int = prefs.getCleanupMode()
    private var lastCleanupTime: Long = prefs.getLastCleanupTime()

    override val reminders: StateFlow<List<ReminderEntity>> =
        repo.observeVisible()
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
                emptyList()
            )

    override fun addTestReminder() {
        scope.launch {
            val r = engine.createFromText("Тест: Dashman живёт (и ему не стыдно)")
            Log.d("Dashman", "addTestReminder -> $r")

            when (r) {
                is CreateResult.Success -> {
                    Log.d(
                        "DashmanBrain",
                        "Reminder created id=${r.reminder.id}, priority=${r.reminder.priority}"
                    )
                    say?.invoke(reminderCreatedPhrase(r.reminder))
                }
                is CreateResult.Error -> {
                    say?.invoke(GreetingPhrases.randomReminderCreationError())
                }
                is CreateResult.NeedClarification -> {
                    say?.invoke(r.message)
                }
                else -> Unit
            }
        }
    }

    override fun addTextReminder(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        scope.launch {
            if (!premiumChecker.isPremium) {
                val activeCount = repo.countActive()
                if (activeCount >= 5) {
                    say?.invoke("Достигнут лимит пяти напоминаний. Для большего количества нужен Premium.")
                    return@launch
                }
            }
            val r = engine.createFromText(t)
            Log.d("Dashman", "addTextReminder('$t') -> $r")

            when (r) {
                is CreateResult.Success -> {
                    Log.d(
                        "DashmanBrain",
                        "Reminder created id=${r.reminder.id}, priority=${r.reminder.priority}"
                    )
                    say?.invoke(reminderCreatedPhrase(r.reminder))
                }
                is CreateResult.Error -> {
                    say?.invoke(GreetingPhrases.randomReminderCreationError())
                }
                is CreateResult.NeedClarification -> {
                    say?.invoke(GreetingPhrases.randomClarification())
                }
                else -> Unit
            }
        }
    }

    override fun speakFilterResult(filter: ReminderFilter, count: Int) {
        say?.invoke(FilterAnswerPhrases.randomFor(filter, count))
    }

    override fun delete(rem: ReminderEntity) {
        scope.launch {
            try {
                engine.delete(rem)
                Log.d("DashmanBrain", "Reminder deleted by user id=${rem.id}")
            } catch (e: Exception) {
                Log.e("DashmanBrain", "Delete failed id=${rem.id}", e)
            }
        }
    }

    fun markReminderDone(rem: ReminderEntity) {
        scope.launch {
            try {
                engine.markDone(rem.id)
                Log.d("DashmanBrain", "Reminder marked done id=${rem.id}")
            } catch (e: Exception) {
                Log.e("DashmanBrain", "Mark done failed id=${rem.id}", e)
            }
        }
    }

    fun cleanupNow() {
        scope.launch {
            try {
                Log.d("DashmanBrain", "Manual cleanup started")
                repo.cleanupDone()
                repo.cleanupFired()
                lastCleanupTime = System.currentTimeMillis()
                prefs.setLastCleanupTime(lastCleanupTime)
                Log.d("DashmanBrain", "Manual cleanup finished successfully")
            } catch (e: Exception) {
                Log.e("DashmanBrain", "Manual cleanup failed", e)
            }
        }
    }

    fun setCleanupMode(mode: Int) {
        cleanupMode = mode
        prefs.setCleanupMode(mode)
        Log.d("DashmanBrain", "Cleanup mode set to $mode")
    }

    fun runAutoCleanupIfNeeded() {
        try {
            Log.d("DashmanBrain", "Auto-cleanup check started, mode=$cleanupMode, last=$lastCleanupTime")

            cleanupMode = prefs.getCleanupMode()
            lastCleanupTime = prefs.getLastCleanupTime()

            val now = System.currentTimeMillis()
            val shouldCleanup = when (cleanupMode) {
                1 -> now - lastCleanupTime >= 24L * 60L * 60L * 1000L
                2 -> now - lastCleanupTime >= 7L * 24L * 60L * 60L * 1000L
                else -> false
            }

            if (!shouldCleanup) {
                Log.d("DashmanBrain", "Auto-cleanup skipped")
                return
            }

            scope.launch {
                try {
                    Log.d("DashmanBrain", "Auto-cleanup started")
                    repo.cleanupDone()
                    repo.cleanupFired()
                    lastCleanupTime = System.currentTimeMillis()
                    prefs.setLastCleanupTime(lastCleanupTime)
                    Log.d("DashmanBrain", "Auto-cleanup finished successfully, lastCleanupTime=$lastCleanupTime")
                } catch (e: Exception) {
                    Log.e("DashmanBrain", "Auto-cleanup launch failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e("DashmanBrain", "Auto-cleanup check failed", e)
        }
    }

    override suspend fun searchByKeyword(keyword: String): List<ReminderEntity> {
        return try {
            repo.searchByKeyword(keyword)
        } catch (e: Exception) {
            Log.e("DashmanBrain", "searchByKeyword failed keyword='$keyword'", e)
            emptyList()
        }
    }

    override fun speak(text: String) {
        say?.invoke(text)
    }

    override suspend fun insertAll(reminders: List<ReminderEntity>): Pair<Int, Int> {
        return try {
            val result = repo.insertAll(reminders)
            Log.d("DashmanBrain", "insertAll: added=${result.first} skipped=${result.second}")
            result
        } catch (e: Exception) {
            Log.e("DashmanBrain", "insertAll failed", e)
            Pair(0, 0)
        }
    }

    override fun close() {
        Log.d("DashmanBrain", "Brain closing, scope cancelled")
        scope.cancel()
    }
}
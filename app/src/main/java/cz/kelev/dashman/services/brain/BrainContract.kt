package cz.kelev.dashman.services.brain

import cz.kelev.dashman.storage.ReminderEntity
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

interface BrainContract : Closeable {
    val reminders: StateFlow<List<ReminderEntity>>

    fun addTestReminder()
    fun addTextReminder(text: String)
    fun speakFilterResult(filter: cz.kelev.dashman.services.filter.ReminderFilter, count: Int)
    fun delete(rem: ReminderEntity)

    // Поиск по ключевому слову
    suspend fun searchByKeyword(keyword: String): List<ReminderEntity>

    // Произнести произвольную фразу
    fun speak(text: String)
    suspend fun insertAll(reminders: List<ReminderEntity>): Pair<Int, Int>
}

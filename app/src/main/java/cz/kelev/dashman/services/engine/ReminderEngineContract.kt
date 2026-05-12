package cz.kelev.dashman.services.engine

import cz.kelev.dashman.storage.ReminderEntity

sealed class CreateResult {
    data class Success(val reminder: ReminderEntity) : CreateResult()
    data class NeedClarification(val message: String) : CreateResult()
    data class Error(val message: String) : CreateResult()
}

interface ReminderEngineContract {
    suspend fun createFromText(text: String, now: Long = System.currentTimeMillis()): CreateResult
    suspend fun delete(rem: ReminderEntity)
    suspend fun markDone(id: Long)
    suspend fun cleanupDone(): Int
    fun scheduleReminder(reminderId: Long, triggerAtMillis: Long)
}
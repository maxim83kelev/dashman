package cz.kelev.dashman.storage

import kotlinx.coroutines.flow.Flow

class ReminderRepository(private val dao: ReminderDao) {
    fun observeVisible(): Flow<List<ReminderEntity>> = dao.observeVisible()

    suspend fun add(text: String): Long {
        return add(text = text, dueAt = null)
    }

    suspend fun add(
        text: String,
        dueAt: Long? = null,
        title: String? = null,
        raw: String? = null,
        priority: String = "normal",
        repeat: String? = null,
        status: String = "active",
        repeatFrom: Long? = null,
        repeatUntil: Long? = null
    ): Long {
        val entity = ReminderEntity(
            text = text,
            dueAt = dueAt,
            title = title,
            raw = raw,
            priority = priority,
            repeat = repeat,
            status = status,
            repeatFrom = repeatFrom,
            repeatUntil = repeatUntil
        )
        return dao.insert(entity)
    }

    suspend fun insert(rem: ReminderEntity) = dao.insert(rem)

    suspend fun delete(rem: ReminderEntity) = dao.delete(rem)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun markDone(id: Long): Int = dao.setDone(id, true)

    suspend fun unmarkDone(id: Long): Int = dao.setDone(id, false)

    suspend fun setStatus(id: Long, status: String): Int = dao.setStatus(id, status)

    suspend fun cleanupDone(): Int = dao.deleteDone()

    suspend fun getById(id: Long): ReminderEntity? = dao.getById(id)

    suspend fun getScheduledActive(): List<ReminderEntity> = dao.getScheduledActive()

    suspend fun cleanupFired(): Int = dao.deleteFired()

    suspend fun searchByKeyword(keyword: String): List<ReminderEntity> =
    dao.searchByKeyword(keyword)

    suspend fun updateDueAt(id: Long, dueAt: Long): Int = dao.updateDueAt(id, dueAt)

    suspend fun updateDueAtAndActivate(id: Long, dueAt: Long): Int = dao.updateDueAtAndActivate(id, dueAt)

    suspend fun updateText(id: Long, text: String): Int = dao.updateText(id, text)

    suspend fun countActive(): Int = dao.countActive()

    suspend fun insertAll(reminders: List<ReminderEntity>): Pair<Int, Int> {
        var added = 0
        var skipped = 0
        reminders.forEach { reminder ->
            val exists = dao.countByTextAndDueAt(reminder.text, reminder.dueAt) > 0
            if (!exists) {
                dao.insert(reminder.copy(id = 0))
                added++
            } else {
                skipped++
            }
        }
        return Pair(added, skipped)
    }
}

package cz.kelev.dashman.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("""
    SELECT * FROM reminders
    WHERE isDone = 0
    AND status IN ('active', 'fired')
    ORDER BY
    CASE WHEN status = 'fired' THEN 0 ELSE 1 END,
    dueAt ASC,
    createdAt DESC
    """)
    fun observeVisible(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity): Int

    @Query("DELETE FROM reminders")
    suspend fun deleteAll(): Int

    @Query("UPDATE reminders SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean): Int

    @Query("UPDATE reminders SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String): Int

    @Query("DELETE FROM reminders WHERE isDone = 1")
    suspend fun deleteDone(): Int

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("""
        SELECT * FROM reminders
        WHERE isDone = 0
        AND dueAt IS NOT NULL
        AND status = 'active'
    """)
    suspend fun getScheduledActive(): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE status = 'fired'")
    suspend fun deleteFired(): Int

    @Query("""
        SELECT * FROM reminders
        WHERE isDone = 0
        AND status IN ('active', 'fired')
        AND text LIKE '%' || :keyword || '%'
        ORDER BY
        CASE WHEN status = 'fired' THEN 0 ELSE 1 END,
        dueAt ASC
    """)
    suspend fun searchByKeyword(keyword: String): List<ReminderEntity>

    @Query("UPDATE reminders SET dueAt = :dueAt WHERE id = :id")
    suspend fun updateDueAt(id: Long, dueAt: Long): Int

    @Query("UPDATE reminders SET dueAt = :dueAt, status = 'active', isDone = 0 WHERE id = :id")
    suspend fun updateDueAtAndActivate(id: Long, dueAt: Long): Int
    
    @Query("UPDATE reminders SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String): Int

    @Query("""
        SELECT COUNT(*) FROM reminders
        WHERE text = :text AND (dueAt = :dueAt OR (dueAt IS NULL AND :dueAt IS NULL))
    """)
    suspend fun countByTextAndDueAt(text: String, dueAt: Long?): Int
    

    @Query("""
        SELECT COUNT(*) FROM reminders
        WHERE isDone = 0
        AND status IN ('active', 'fired')
    """)
    suspend fun countActive(): Int
}
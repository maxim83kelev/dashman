package cz.kelev.dashman.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.Ignore
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["isDone", "createdAt"]),
        Index(value = ["isDone", "dueAt", "status"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
    val isDone: Boolean = false,
    val title: String? = null,
    val raw: String? = null,
    val priority: String = "normal", // critical | important | normal
    val repeat: String? = null,
    val status: String = "active",   // active | fired | done

    /**
     * Время начала повторений в миллисекундах.
     * Если null — повторения начинаются сразу (от dueAt).
     * Используется для «начиная с 9:00» или «с 1 апреля».
     */
    val repeatFrom: Long? = null,

    /**
     * Время окончания повторений в миллисекундах.
     * Если null — повторяется бесконечно.
     * Используется для «до 18:00» или «по 30 апреля».
     */
    val repeatUntil: Long? = null
)

object ReminderPriority {
    const val CRITICAL = "critical"
    const val IMPORTANT = "important"
    const val NORMAL = "normal"
}

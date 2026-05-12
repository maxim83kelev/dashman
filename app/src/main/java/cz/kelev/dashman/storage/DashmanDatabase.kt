package cz.kelev.dashman.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReminderEntity::class],
    version = 4,
    exportSchema = true
)
abstract class DashmanDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile private var INSTANCE: DashmanDatabase? = null

        fun get(context: Context): DashmanDatabase {
            INSTANCE?.let { return it }

            return synchronized(this) {
                val existing = INSTANCE
                if (existing != null) return@synchronized existing

                val created = Room.databaseBuilder(
                    context.applicationContext,
                    DashmanDatabase::class.java,
                    "dashman.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()

                INSTANCE = created
                created
            }
        }

        fun reset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_reminders_isDone_createdAt ON reminders (isDone, createdAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_reminders_isDone_dueAt_status ON reminders (isDone, dueAt, status)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем поля для диапазона повторений
                db.execSQL("ALTER TABLE reminders ADD COLUMN repeatFrom INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN repeatUntil INTEGER")
            }
        }
    }
}
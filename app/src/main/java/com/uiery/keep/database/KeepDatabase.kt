package com.uiery.keep.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.uiery.keep.database.converter.DayOfWeekTypeConverter
import com.uiery.keep.database.converter.ListStringTypeConverter
import com.uiery.keep.database.converter.TimeTypeConverter
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.database.entity.RoutineEntity

@Database(
    entities = [
        RoutineEntity::class,
        LockHistoryEntity::class,
        EmergencyUnlockEntity::class,
    ],
    version = 4,
)
@TypeConverters(
    value = [
        TimeTypeConverter::class,
        DayOfWeekTypeConverter::class,
        ListStringTypeConverter::class
    ],
)
abstract class KeepDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun lockHistoryDao(): LockHistoryDao
    abstract fun emergencyUnlockDao(): EmergencyUnlockDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lock_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        start_timestamp INTEGER NOT NULL,
                        end_timestamp INTEGER NOT NULL,
                        duration_millis INTEGER NOT NULL,
                        locked_apps TEXT NOT NULL,
                        is_routine INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routine ADD COLUMN change_lock_hours INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS emergency_unlock (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        custom_reason TEXT,
                        unlocked_apps TEXT NOT NULL,
                        duration_minutes INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
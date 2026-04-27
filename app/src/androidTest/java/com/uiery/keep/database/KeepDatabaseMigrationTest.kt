package com.uiery.keep.database

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeepDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KeepDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesFromVersion1ToLatestPreservingRoutineData() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertRoutineV1(id = 1)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            LATEST_VERSION,
            true,
            KeepDatabase.MIGRATION_1_2,
            KeepDatabase.MIGRATION_2_3,
            KeepDatabase.MIGRATION_3_4,
        )

        db.query("SELECT * FROM routine WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Morning focus", cursor.stringValue("name"))
            assertEquals("09:00", cursor.stringValue("start_time"))
            assertEquals("10:00", cursor.stringValue("end_time"))
            assertEquals("MONDAY,WEDNESDAY", cursor.stringValue("repeatDays"))
            assertEquals("com.chat,com.video", cursor.stringValue("lockApplications"))
            assertEquals(1, cursor.intValue("is_enabled"))
            assertEquals(true, cursor.isNull(cursor.getColumnIndexOrThrow("change_lock_hours")))
        }
        db.close()
    }

    @Test
    fun migratesFromVersion2ToLatestPreservingLockHistoryData() {
        helper.createDatabase(TEST_DB, 2).apply {
            insertRoutineV1(id = 2)
            insertLockHistory(id = 10)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            LATEST_VERSION,
            true,
            KeepDatabase.MIGRATION_2_3,
            KeepDatabase.MIGRATION_3_4,
        )

        db.query("SELECT * FROM lock_history WHERE id = 10").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1000L, cursor.longValue("start_timestamp"))
            assertEquals(4000L, cursor.longValue("end_timestamp"))
            assertEquals(3000L, cursor.longValue("duration_millis"))
            assertEquals("com.chat,com.video", cursor.stringValue("locked_apps"))
            assertEquals(0, cursor.intValue("is_routine"))
        }
        db.close()
    }

    @Test
    fun migratesFromVersion3ToLatestPreservingChangeLockHours() {
        helper.createDatabase(TEST_DB, 3).apply {
            insertRoutineV3(id = 3, changeLockHours = 2)
            insertLockHistory(id = 11)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            LATEST_VERSION,
            true,
            KeepDatabase.MIGRATION_3_4,
        )

        db.query("SELECT * FROM routine WHERE id = 3").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Morning focus", cursor.stringValue("name"))
            assertEquals(2, cursor.intValue("change_lock_hours"))
        }
        db.close()
    }

    @Test
    fun validatesVersion4SchemaAndPreservesEmergencyUnlockData() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertRoutineV3(id = 4, changeLockHours = 3)
            insertLockHistory(id = 12)
            insertEmergencyUnlock(id = 20)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, LATEST_VERSION, true)

        db.query("SELECT * FROM emergency_unlock WHERE id = 20").use { cursor ->
            cursor.moveToFirst()
            assertEquals(5000L, cursor.longValue("timestamp"))
            assertEquals("urgent", cursor.stringValue("reason"))
            assertEquals("custom note", cursor.stringValue("custom_reason"))
            assertEquals("com.chat,com.video", cursor.stringValue("unlocked_apps"))
            assertEquals(10, cursor.intValue("duration_minutes"))
        }
        db.close()
    }

    private fun SupportSQLiteDatabase.insertRoutineV1(id: Long) {
        execSQL(
            """
            INSERT INTO routine (id, name, start_time, end_time, repeatDays, lockApplications, is_enabled)
            VALUES ($id, 'Morning focus', '09:00', '10:00', 'MONDAY,WEDNESDAY', 'com.chat,com.video', 1)
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertRoutineV3(id: Long, changeLockHours: Int) {
        execSQL(
            """
            INSERT INTO routine (
                id, name, start_time, end_time, repeatDays, lockApplications, is_enabled, change_lock_hours
            )
            VALUES (
                $id, 'Morning focus', '09:00', '10:00', 'MONDAY,WEDNESDAY', 'com.chat,com.video', 1, $changeLockHours
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertLockHistory(id: Long) {
        execSQL(
            """
            INSERT INTO lock_history (
                id, start_timestamp, end_timestamp, duration_millis, locked_apps, is_routine
            )
            VALUES ($id, 1000, 4000, 3000, 'com.chat,com.video', 0)
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertEmergencyUnlock(id: Long) {
        execSQL(
            """
            INSERT INTO emergency_unlock (
                id, timestamp, reason, custom_reason, unlocked_apps, duration_minutes
            )
            VALUES ($id, 5000, 'urgent', 'custom note', 'com.chat,com.video', 10)
            """.trimIndent(),
        )
    }

    private fun Cursor.stringValue(columnName: String): String = getString(getColumnIndexOrThrow(columnName))

    private fun Cursor.intValue(columnName: String): Int = getInt(getColumnIndexOrThrow(columnName))

    private fun Cursor.longValue(columnName: String): Long = getLong(getColumnIndexOrThrow(columnName))

    companion object {
        private const val TEST_DB = "keep-migration-test"
        private const val LATEST_VERSION = 4
    }
}

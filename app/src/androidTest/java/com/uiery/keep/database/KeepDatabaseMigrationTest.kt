package com.uiery.keep.database

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            KeepDatabase.MIGRATION_4_5,
            KeepDatabase.MIGRATION_5_6,
            KeepDatabase.MIGRATION_6_7,
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
            KeepDatabase.MIGRATION_4_5,
            KeepDatabase.MIGRATION_5_6,
            KeepDatabase.MIGRATION_6_7,
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
            KeepDatabase.MIGRATION_4_5,
            KeepDatabase.MIGRATION_5_6,
            KeepDatabase.MIGRATION_6_7,
        )

        db.query("SELECT * FROM routine WHERE id = 3").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Morning focus", cursor.stringValue("name"))
            assertEquals(2, cursor.intValue("change_lock_hours"))
        }
        db.close()
    }

    @Test
    fun migratesFromVersion4ToLatestAddingGoalLockTableAndPreservingEmergencyUnlockData() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertRoutineV3(id = 4, changeLockHours = 3)
            insertLockHistory(id = 12)
            insertEmergencyUnlock(id = 20)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            LATEST_VERSION,
            true,
            KeepDatabase.MIGRATION_4_5,
            KeepDatabase.MIGRATION_5_6,
            KeepDatabase.MIGRATION_6_7,
        )

        db.query("SELECT * FROM emergency_unlock WHERE id = 20").use { cursor ->
            cursor.moveToFirst()
            assertEquals(5000L, cursor.longValue("timestamp"))
            assertEquals("urgent", cursor.stringValue("reason"))
            assertEquals("custom note", cursor.stringValue("custom_reason"))
            assertEquals("com.chat,com.video", cursor.stringValue("unlocked_apps"))
            assertEquals(10, cursor.intValue("duration_minutes"))
        }
        db.query("SELECT COUNT(*) AS count FROM goal_lock").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.intValue("count"))
        }
        db.close()
    }

    @Test
    fun migratesFromVersion5ToLatestAddingAppUsageDailyTableAndPreservingGoalLockData() {
        helper.createDatabase(TEST_DB, 5).apply {
            insertRoutineV3(id = 5, changeLockHours = 4)
            insertLockHistory(id = 13)
            insertEmergencyUnlock(id = 21)
            insertGoalLock(id = 30)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            LATEST_VERSION,
            true,
            KeepDatabase.MIGRATION_5_6,
            KeepDatabase.MIGRATION_6_7,
        )

        db.query("SELECT * FROM goal_lock WHERE id = 30").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Focus sprint", cursor.stringValue("goal_name"))
            assertEquals("2026-01-01", cursor.stringValue("start_date"))
            assertEquals("2026-01-31", cursor.stringValue("end_date"))
            assertEquals("ALWAYS", cursor.stringValue("lock_mode"))
            assertEquals("com.chat,com.video", cursor.stringValue("selected_packages"))
            assertEquals("ACTIVE", cursor.stringValue("status"))
        }
        db.query("SELECT COUNT(*) AS count FROM app_usage_daily").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.intValue("count"))
        }
        db.close()
    }

    @Test
    fun migratesFromVersion6ToLatestPreservingDataAndAddingFirstPromiseTables() {
        helper.createDatabase(TEST_DB, 6).apply {
            insertRoutineV3(id = 6, changeLockHours = 5)
            insertGoalLock(id = 31)
            insertAppUsageDaily()
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            LATEST_VERSION,
            true,
            KeepDatabase.MIGRATION_6_7,
        )

        db.query("SELECT * FROM routine WHERE id = 6").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Morning focus", cursor.stringValue("name"))
            assertEquals(5, cursor.intValue("change_lock_hours"))
        }
        db.query("SELECT * FROM goal_lock WHERE id = 31").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Focus sprint", cursor.stringValue("goal_name"))
            assertEquals("ACTIVE", cursor.stringValue("status"))
        }
        db.query("SELECT * FROM app_usage_daily WHERE date = '2026-07-14'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("com.chat", cursor.stringValue("package_name"))
            assertEquals(3_600_000L, cursor.longValue("total_usage_millis"))
            assertEquals(12, cursor.intValue("launch_count"))
            assertEquals(600_000L, cursor.longValue("night_usage_millis"))
        }

        assertTableExists(db, "first_promise")
        assertTableExists(db, "first_promise_analytics_outbox")
        assertIndexExists(db, "first_promise", "index_first_promise_routine_id", unique = true)
        assertIndexExists(
            db,
            "first_promise_analytics_outbox",
            "index_first_promise_analytics_outbox_draft_id_sequence",
            unique = false,
        )

        // MigrationTestHelper returns a raw connection; Room enables this on normal database opens.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            """
            INSERT INTO first_promise (
                draft_id, routine_id, goal_type, source, created_at_millis
            ) VALUES ('draft-1', 6, 'focus', 'personalized', 1000)
            """.trimIndent(),
        )
        listOf(30, 10, 40, 20).forEach { sequence ->
            db.insertFirstPromiseOutbox(sequence)
        }

        db.query(
            """
            SELECT sequence FROM first_promise_analytics_outbox
            WHERE draft_id = 'draft-1'
            ORDER BY sequence
            """.trimIndent(),
        ).use { cursor ->
            val sequences = buildList {
                while (cursor.moveToNext()) add(cursor.intValue("sequence"))
            }
            assertEquals(listOf(10, 20, 30, 40), sequences)
        }

        db.execSQL("DELETE FROM routine WHERE id = 6")
        db.query("SELECT COUNT(*) AS count FROM first_promise WHERE draft_id = 'draft-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.intValue("count"))
        }
        db.query(
            "SELECT COUNT(*) AS count FROM first_promise_analytics_outbox WHERE draft_id = 'draft-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(4, cursor.intValue("count"))
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

    private fun SupportSQLiteDatabase.insertGoalLock(id: Long) {
        execSQL(
            """
            INSERT INTO goal_lock (
                id, goal_name, start_date, end_date, lock_mode, repeat_days, start_time, end_time, selected_packages, status
            )
            VALUES ($id, 'Focus sprint', '2026-01-01', '2026-01-31', 'ALWAYS', NULL, NULL, NULL, 'com.chat,com.video', 'ACTIVE')
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertAppUsageDaily() {
        execSQL(
            """
            INSERT INTO app_usage_daily (
                date, package_name, total_usage_millis, launch_count, night_usage_millis
            ) VALUES ('2026-07-14', 'com.chat', 3600000, 12, 600000)
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertFirstPromiseOutbox(sequence: Int) {
        execSQL(
            """
            INSERT INTO first_promise_analytics_outbox (
                draft_id, event_name, sequence, canonical_event_name, payload_json,
                occurred_at_millis, delivery_state, sent_at_millis
            ) VALUES (
                'draft-1', 'event-$sequence', $sequence, 'canonical-$sequence', '{}',
                $sequence, 'pending', NULL
            )
            """.trimIndent(),
        )
    }

    private fun assertTableExists(db: SupportSQLiteDatabase, tableName: String) {
        db.query(
            "SELECT COUNT(*) AS count FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.intValue("count"))
        }
    }

    private fun assertIndexExists(
        db: SupportSQLiteDatabase,
        tableName: String,
        indexName: String,
        unique: Boolean,
    ) {
        db.query("PRAGMA index_list(`$tableName`)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.stringValue("name") == indexName) {
                    assertEquals(if (unique) 1 else 0, cursor.intValue("unique"))
                    found = true
                }
            }
            assertTrue("Expected index $indexName on $tableName", found)
        }
    }

    private fun Cursor.stringValue(columnName: String): String = getString(getColumnIndexOrThrow(columnName))

    private fun Cursor.intValue(columnName: String): Int = getInt(getColumnIndexOrThrow(columnName))

    private fun Cursor.longValue(columnName: String): Long = getLong(getColumnIndexOrThrow(columnName))

    companion object {
        private const val TEST_DB = "keep-migration-test"
        private const val LATEST_VERSION = 7
    }
}

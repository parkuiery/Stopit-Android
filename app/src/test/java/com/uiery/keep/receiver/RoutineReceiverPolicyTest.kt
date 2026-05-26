package com.uiery.keep.receiver

import android.content.Intent
import com.uiery.keep.notification.RoutineStartNotificationResult
import com.uiery.keep.model.RoutineModel
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineReceiverPolicyTest {

    @Test
    fun shouldRestoreRoutinesOnBootReturnsTrueForBootCompletedAndMyPackageReplacedActions() {
        assertEquals(true, RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(true, RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(false, RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(null))
    }

    @Test
    fun parseRoutineAlarmTriggerReturnsPayloadForValidAlarmActionAndExtras() {
        assertEquals(
            RoutineAlarmTrigger(routineName = "Morning focus", routineId = 42L),
            RoutineReceiverPolicy.parseRoutineAlarmTrigger(
                action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM,
                routineName = "Morning focus",
                routineId = 42L,
            ),
        )
    }

    @Test
    fun parseRoutineAlarmTriggerReturnsNullWhenActionDoesNotMatch() {
        assertNull(
            RoutineReceiverPolicy.parseRoutineAlarmTrigger(
                action = Intent.ACTION_BOOT_COMPLETED,
                routineName = "Morning focus",
                routineId = 42L,
            ),
        )
    }

    @Test
    fun parseRoutineAlarmTriggerReturnsNullWhenRoutineNameMissing() {
        assertNull(
            RoutineReceiverPolicy.parseRoutineAlarmTrigger(
                action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM,
                routineName = null,
                routineId = 42L,
            ),
        )
    }

    @Test
    fun parseRoutineAlarmTriggerReturnsNullWhenRoutineIdMissing() {
        assertNull(
            RoutineReceiverPolicy.parseRoutineAlarmTrigger(
                action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM,
                routineName = "Morning focus",
                routineId = -1L,
            ),
        )
    }

    @Test
    fun decodeStoredRoutinesReturnsEmptyListWhenJsonIsNull() {
        assertEquals(emptyList<RoutineModel>(), RoutineReceiverPolicy.decodeStoredRoutines(null))
    }

    @Test
    fun decodeStoredRoutinesReturnsEmptyListWhenJsonIsBlank() {
        assertEquals(emptyList<RoutineModel>(), RoutineReceiverPolicy.decodeStoredRoutines("   "))
    }

    @Test
    fun decodeStoredRoutinesReturnsEmptyListWhenJsonIsMalformed() {
        assertEquals(emptyList<RoutineModel>(), RoutineReceiverPolicy.decodeStoredRoutines("not-json"))
    }

    @Test
    fun decodeStoredRoutinesParsesSavedRoutines() {
        val routines = listOf(
            routine(id = 1L, name = "Morning focus", isEnabled = true),
            routine(id = 2L, name = "Evening focus", isEnabled = false),
        )

        val json = Json.encodeToString(routines)

        assertEquals(routines, RoutineReceiverPolicy.decodeStoredRoutines(json))
    }

    @Test
    fun findEnabledRoutineToRescheduleReturnsMatchingEnabledRoutine() {
        val disabled = routine(id = 1L, name = "Disabled", isEnabled = false)
        val enabled = routine(id = 2L, name = "Enabled", isEnabled = true)

        val routine = RoutineReceiverPolicy.findEnabledRoutineToReschedule(
            routines = listOf(disabled, enabled),
            routineId = 2L,
        )

        assertEquals(enabled, routine)
    }

    @Test
    fun findEnabledRoutineToRescheduleReturnsNullForDisabledRoutine() {
        val disabled = routine(id = 5L, name = "Disabled", isEnabled = false)

        val routine = RoutineReceiverPolicy.findEnabledRoutineToReschedule(
            routines = listOf(disabled),
            routineId = 5L,
        )

        assertNull(routine)
    }

    @Test
    fun findEnabledRoutineToRescheduleReturnsNullWhenRoutineIdMissing() {
        val enabled = routine(id = 7L, name = "Enabled", isEnabled = true)

        val routine = RoutineReceiverPolicy.findEnabledRoutineToReschedule(
            routines = listOf(enabled),
            routineId = 99L,
        )

        assertNull(routine)
    }

    @Test
    fun resolveRoutinesPrefersDatabaseRoutinesWhenPresentEvenIfStoredRoutinesExist() {
        val stored = listOf(routine(id = 1L, name = "Stored", isEnabled = true))
        val database = listOf(routine(id = 2L, name = "Database", isEnabled = true))

        assertEquals(database, RoutineReceiverPolicy.resolveRoutines(storedRoutines = stored, databaseRoutines = database))
    }

    @Test
    fun resolveRoutinesFallsBackToDatabaseRoutinesWhenStoredRoutinesEmpty() {
        val database = listOf(routine(id = 2L, name = "Database", isEnabled = true))

        assertEquals(
            database,
            RoutineReceiverPolicy.resolveRoutines(storedRoutines = emptyList(), databaseRoutines = database),
        )
    }

    @Test
    fun shouldRehydrateStoredRoutinesReturnsTrueWhenDatabaseRoutinesReplaceMissingStoredRoutines() {
        val database = listOf(routine(id = 3L, name = "Database", isEnabled = true))

        assertEquals(
            true,
            RoutineReceiverPolicy.shouldRehydrateStoredRoutines(
                storedRoutines = emptyList(),
                databaseRoutines = database,
            ),
        )
    }

    @Test
    fun shouldRehydrateStoredRoutinesReturnsTrueWhenDatabaseRoutinesReplaceStaleStoredRoutines() {
        val stored = listOf(routine(id = 4L, name = "Stored", isEnabled = true))
        val database = listOf(routine(id = 5L, name = "Database", isEnabled = true))

        assertEquals(
            true,
            RoutineReceiverPolicy.shouldRehydrateStoredRoutines(
                storedRoutines = stored,
                databaseRoutines = database,
            ),
        )
    }

    @Test
    fun shouldRehydrateStoredRoutinesReturnsFalseWhenStoredRoutinesAlreadyMatchDatabase() {
        val database = listOf(routine(id = 6L, name = "Database", isEnabled = true))

        assertEquals(
            false,
            RoutineReceiverPolicy.shouldRehydrateStoredRoutines(
                storedRoutines = database,
                databaseRoutines = database,
            ),
        )
    }

    @Test
    fun buildPendingRoutineStartNoticeReturnsPendingNoticeWhenNotificationPermissionDenied() {
        assertEquals(
            PendingRoutineStartNotice(message = "Routine started without notification permission"),
            RoutineReceiverPolicy.buildPendingRoutineStartNotice(
                notificationResult = RoutineStartNotificationResult.PermissionDenied,
                fallbackMessage = "Routine started without notification permission",
            ),
        )
    }

    @Test
    fun buildPendingRoutineStartNoticeReturnsNullWhenNotificationPosted() {
        assertNull(
            RoutineReceiverPolicy.buildPendingRoutineStartNotice(
                notificationResult = RoutineStartNotificationResult.Posted,
                fallbackMessage = "Should not be used",
            ),
        )
    }

    @Test
    fun buildPendingRoutineStartNoticeReturnsNullWhenFallbackMessageBlank() {
        assertNull(
            RoutineReceiverPolicy.buildPendingRoutineStartNotice(
                notificationResult = RoutineStartNotificationResult.PermissionDenied,
                fallbackMessage = "  ",
            ),
        )
    }

    private fun routine(
        id: Long,
        name: String,
        isEnabled: Boolean,
    ) = RoutineModel(
        id = id,
        name = name,
        startTime = LocalTime(hour = 9, minute = 0),
        endTime = LocalTime(hour = 10, minute = 0),
        repeatDays = "1000000",
        lockApplications = listOf("com.example.app"),
        isEnabled = isEnabled,
        changeLockHours = null,
    )
}

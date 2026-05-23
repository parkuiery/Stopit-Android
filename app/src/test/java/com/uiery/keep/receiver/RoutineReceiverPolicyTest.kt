package com.uiery.keep.receiver

import com.uiery.keep.model.RoutineModel
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineReceiverPolicyTest {

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

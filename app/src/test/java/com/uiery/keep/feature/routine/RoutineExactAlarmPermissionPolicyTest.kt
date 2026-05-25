package com.uiery.keep.feature.routine

import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class RoutineExactAlarmPermissionPolicyTest {
    @Test
    fun enabledRoutineWithoutExactAlarmPermissionFallsBackToDisabledAndRequestsPrompt() {
        val result = resolveRoutineExactAlarmPermission(
            routine = testRoutine(isEnabled = true),
            canScheduleExactAlarms = false,
        )

        assertFalse(result.routine.isEnabled)
        assertTrue(result.shouldShowPermissionPrompt)
    }

    @Test
    fun enabledRoutineWithExactAlarmPermissionKeepsEnabledState() {
        val result = resolveRoutineExactAlarmPermission(
            routine = testRoutine(isEnabled = true),
            canScheduleExactAlarms = true,
        )

        assertTrue(result.routine.isEnabled)
        assertFalse(result.shouldShowPermissionPrompt)
    }

    @Test
    fun disabledRoutineNeverRequestsExactAlarmPermission() {
        val result = resolveRoutineExactAlarmPermission(
            routine = testRoutine(isEnabled = false),
            canScheduleExactAlarms = false,
        )

        assertEquals(testRoutine(isEnabled = false), result.routine)
        assertFalse(result.shouldShowPermissionPrompt)
    }

    private fun testRoutine(isEnabled: Boolean) = RoutineModel(
        id = 7L,
        name = "Morning focus",
        startTime = LocalTime(hour = 9, minute = 0),
        endTime = LocalTime(hour = 9, minute = 30),
        repeatDays = listOf(DayOfWeek.MONDAY).toRepeatDaysBinary(),
        lockApplications = listOf("com.example.blocked"),
        isEnabled = isEnabled,
        changeLockHours = null,
    )
}

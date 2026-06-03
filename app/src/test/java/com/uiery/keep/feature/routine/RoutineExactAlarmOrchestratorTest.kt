package com.uiery.keep.feature.routine

import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.time.DayOfWeek

class RoutineExactAlarmOrchestratorTest {
    @Test
    fun resolveBeforePersistDisablesEnabledRoutineAndRequestsPromptWhenExactAlarmPermissionIsMissing() {
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(scheduler.canScheduleExactAlarms()).thenReturn(false)
        val orchestrator = RoutineExactAlarmOrchestrator(scheduler)

        val result = orchestrator.resolveBeforePersist(routine(isEnabled = true))

        assertFalse(result.routine.isEnabled)
        assertTrue(result.shouldShowPermissionPrompt)
    }

    @Test
    fun scheduleEnabledRoutineTracksScheduledRoutineWhenAlarmSchedulingSucceeds() {
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        val enabledRoutine = routine(isEnabled = true)
        Mockito.`when`(scheduler.scheduleRoutine(enabledRoutine))
            .thenReturn(RoutineScheduleResult.Scheduled)
        val orchestrator = RoutineExactAlarmOrchestrator(scheduler)

        val result = orchestrator.scheduleEnabledRoutine(enabledRoutine)

        assertEquals(enabledRoutine, result.routine)
        assertTrue(result.shouldTrackLockScheduled)
        assertFalse(result.shouldShowPermissionPrompt)
    }

    @Test
    fun scheduleEnabledRoutineDisablesRoutineAndRequestsPromptWhenSchedulerReportsMissingPermission() {
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        val enabledRoutine = routine(isEnabled = true)
        Mockito.`when`(scheduler.scheduleRoutine(enabledRoutine))
            .thenReturn(RoutineScheduleResult.MissingExactAlarmPermission)
        val orchestrator = RoutineExactAlarmOrchestrator(scheduler)

        val result = orchestrator.scheduleEnabledRoutine(enabledRoutine)

        assertFalse(result.routine.isEnabled)
        assertFalse(result.shouldTrackLockScheduled)
        assertTrue(result.shouldShowPermissionPrompt)
    }

    @Test
    fun scheduleEnabledRoutineLeavesDisabledRoutineUnscheduledWithoutPrompt() {
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        val disabledRoutine = routine(isEnabled = false)
        val orchestrator = RoutineExactAlarmOrchestrator(scheduler)

        val result = orchestrator.scheduleEnabledRoutine(disabledRoutine)

        assertEquals(disabledRoutine, result.routine)
        assertFalse(result.shouldTrackLockScheduled)
        assertFalse(result.shouldShowPermissionPrompt)
        Mockito.verify(scheduler, Mockito.never()).scheduleRoutine(disabledRoutine)
    }

    private fun routine(
        id: Long = 10L,
        isEnabled: Boolean = true,
    ) = RoutineModel(
        id = id,
        name = "Morning focus",
        startTime = LocalTime(hour = 9, minute = 0),
        endTime = LocalTime(hour = 9, minute = 30),
        repeatDays = listOf(DayOfWeek.MONDAY).toRepeatDaysBinary(),
        lockApplications = listOf("com.example.blocked"),
        isEnabled = isEnabled,
        changeLockHours = 2,
    )
}

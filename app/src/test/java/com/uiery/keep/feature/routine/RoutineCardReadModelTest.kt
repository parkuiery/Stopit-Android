package com.uiery.keep.feature.routine

import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class RoutineCardReadModelTest {
    @Test
    fun enabledRoutineExposesRepeatDaysAndNextRunTodayWhenStartIsStillAhead() {
        val now = LocalDateTime.of(2026, 6, 1, 6, 30) // Monday
        val routine = testRoutine(
            startTime = LocalTime(hour = 7, minute = 0),
            endTime = LocalTime(hour = 8, minute = 0),
            repeatDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            isEnabled = true,
        )

        val readModel = routine.toRoutineCardReadModel(now = now)

        assertEquals(RoutineCardStatus.Enabled, readModel.status)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), readModel.repeatDays)
        assertEquals(LocalDateTime.of(2026, 6, 1, 7, 0), readModel.nextRunAt)
    }

    @Test
    fun runningRoutineExposesRunningStatusAndNextRunAfterCurrentWindow() {
        val now = LocalDateTime.of(2026, 6, 1, 7, 30) // Monday
        val routine = testRoutine(
            startTime = LocalTime(hour = 7, minute = 0),
            endTime = LocalTime(hour = 8, minute = 0),
            repeatDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            isEnabled = true,
        )

        val readModel = routine.toRoutineCardReadModel(now = now)

        assertEquals(RoutineCardStatus.Running, readModel.status)
        assertEquals(LocalDateTime.of(2026, 6, 3, 7, 0), readModel.nextRunAt)
    }

    @Test
    fun disabledRoutineExposesDisabledStatusAndNoNextRun() {
        val now = LocalDateTime.of(2026, 6, 1, 6, 30) // Monday
        val routine = testRoutine(
            startTime = LocalTime(hour = 7, minute = 0),
            endTime = LocalTime(hour = 8, minute = 0),
            repeatDays = listOf(DayOfWeek.MONDAY),
            isEnabled = false,
        )

        val readModel = routine.toRoutineCardReadModel(now = now)

        assertEquals(RoutineCardStatus.Disabled, readModel.status)
        assertEquals(listOf(DayOfWeek.MONDAY), readModel.repeatDays)
        assertNull(readModel.nextRunAt)
    }

    @Test
    fun overnightRunningRoutineUsesNextRepeatAfterOvernightWindowStartedYesterday() {
        val now = LocalDateTime.of(2026, 6, 2, 1, 30) // Tuesday, Monday overnight routine still running
        val routine = testRoutine(
            startTime = LocalTime(hour = 23, minute = 0),
            endTime = LocalTime(hour = 2, minute = 0),
            repeatDays = listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            isEnabled = true,
        )

        val readModel = routine.toRoutineCardReadModel(now = now)

        assertEquals(RoutineCardStatus.Running, readModel.status)
        assertEquals(LocalDateTime.of(2026, 6, 4, 23, 0), readModel.nextRunAt)
    }

    private fun testRoutine(
        startTime: LocalTime,
        endTime: LocalTime,
        repeatDays: List<DayOfWeek>,
        isEnabled: Boolean,
    ) = RoutineModel(
        id = 466L,
        name = "Morning focus",
        startTime = startTime,
        endTime = endTime,
        repeatDays = repeatDays.toRepeatDaysBinary(),
        lockApplications = listOf("com.example.blocked"),
        isEnabled = isEnabled,
        changeLockHours = null,
    )
}

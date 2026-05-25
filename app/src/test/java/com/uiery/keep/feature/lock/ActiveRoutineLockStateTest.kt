package com.uiery.keep.feature.lock

import com.uiery.keep.model.RoutineModel
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ActiveRoutineLockStateTest {
    @Test
    fun resolveActiveRoutineLockStateMergesConcurrentRoutineAppsAndUsesLatestEndTime() {
        val nowDateTime = LocalDateTime.of(2026, 5, 25, 9, 45)
        val focusRoutine =
            routine(
                id = 1,
                name = "Morning focus",
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
                repeatDays = listOf(DayOfWeek.MONDAY),
                lockApplications = listOf("com.instagram", "com.youtube"),
            )
        val studyRoutine =
            routine(
                id = 2,
                name = "Study sprint",
                startHour = 9,
                startMinute = 30,
                endHour = 11,
                endMinute = 0,
                repeatDays = listOf(DayOfWeek.MONDAY),
                lockApplications = listOf("com.youtube", "com.discord"),
            )

        val resolved =
            resolveActiveRoutineLockState(
                routines = listOf(focusRoutine, studyRoutine),
                nowDateTime = nowDateTime,
            )

        assertEquals(listOf(focusRoutine, studyRoutine), resolved.routines)
        assertEquals(linkedSetOf("com.instagram", "com.youtube", "com.discord"), resolved.blockedApps)
        assertEquals(LocalDateTime.of(2026, 5, 25, 11, 0), resolved.endTime)
    }

    @Test
    fun resolveActiveRoutineLockStateReturnsEmptyBlockedAppsWhenNothingIsActive() {
        val nowDateTime = LocalDateTime.of(2026, 5, 25, 12, 0)
        val completedRoutine =
            routine(
                id = 1,
                name = "Morning focus",
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
                repeatDays = listOf(DayOfWeek.MONDAY),
                lockApplications = listOf("com.instagram"),
            )
        val disabledRoutine =
            routine(
                id = 2,
                name = "Lunch break",
                startHour = 12,
                startMinute = 0,
                endHour = 13,
                endMinute = 0,
                repeatDays = listOf(DayOfWeek.MONDAY),
                lockApplications = listOf("com.discord"),
                isEnabled = false,
            )

        val resolved =
            resolveActiveRoutineLockState(
                routines = listOf(completedRoutine, disabledRoutine),
                nowDateTime = nowDateTime,
            )

        assertEquals(emptyList<RoutineModel>(), resolved.routines)
        assertEquals(emptySet<String>(), resolved.blockedApps)
        assertEquals(nowDateTime, resolved.endTime)
    }

    private fun routine(
        id: Long,
        name: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        repeatDays: List<DayOfWeek>,
        lockApplications: List<String>,
        isEnabled: Boolean = true,
    ) = RoutineModel(
        id = id,
        name = name,
        startTime = LocalTime(hour = startHour, minute = startMinute),
        endTime = LocalTime(hour = endHour, minute = endMinute),
        repeatDays = DayOfWeek.entries.joinToString("") { day -> if (day in repeatDays) "1" else "0" },
        lockApplications = lockApplications,
        isEnabled = isEnabled,
    )
}

package com.uiery.keep.feature.goallock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class GoalLockPolicyTest {

    @Test
    fun allDayGoalLockBlocksSelectedAppsOnlyDuringInclusiveDateRange() {
        val goalLock = goalLock(
            startDate = LocalDate.of(2026, 6, 4),
            endDate = LocalDate.of(2026, 6, 10),
            selectedPackages = setOf("com.video.app", "com.social.app"),
        )

        assertFalse(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.video.app",
                now = LocalDateTime.of(2026, 6, 3, 23, 59),
            ),
        )
        assertTrue(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.video.app",
                now = LocalDateTime.of(2026, 6, 4, 0, 0),
            ),
        )
        assertFalse(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.chat.app",
                now = LocalDateTime.of(2026, 6, 5, 12, 0),
            ),
        )
        assertTrue(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.social.app",
                now = LocalDateTime.of(2026, 6, 10, 23, 59),
            ),
        )
        assertFalse(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.social.app",
                now = LocalDateTime.of(2026, 6, 11, 0, 0),
            ),
        )
    }

    @Test
    fun scheduledGoalLockBlocksOnlyOnSelectedDaysAndTimeWindow() {
        val goalLock = goalLock(
            lockMode = GoalLockMode.Scheduled(
                repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(23, 0),
            ),
        )

        assertTrue(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.video.app",
                now = LocalDateTime.of(2026, 6, 8, 20, 30),
            ),
        )
        assertFalse(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.video.app",
                now = LocalDateTime.of(2026, 6, 8, 18, 59),
            ),
        )
        assertFalse(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.video.app",
                now = LocalDateTime.of(2026, 6, 9, 20, 30),
            ),
        )
    }

    @Test
    fun overnightScheduledGoalLockCarriesPreviousDayWindowIntoNextMorning() {
        val goalLock = goalLock(
            lockMode = GoalLockMode.Scheduled(
                repeatDays = setOf(DayOfWeek.FRIDAY),
                startTime = LocalTime.of(22, 0),
                endTime = LocalTime.of(2, 0),
            ),
            startDate = LocalDate.of(2026, 6, 5),
            endDate = LocalDate.of(2026, 6, 6),
        )

        assertTrue(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.video.app",
                now = LocalDateTime.of(2026, 6, 5, 23, 30),
            ),
        )
        assertTrue(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.video.app",
                now = LocalDateTime.of(2026, 6, 6, 1, 30),
            ),
        )
        assertFalse(
            GoalLockPolicy.isBlocking(
                goalLock = goalLock,
                packageName = "com.video.app",
                now = LocalDateTime.of(2026, 6, 6, 2, 0),
            ),
        )
    }

    @Test
    fun runtimeStatusSeparatesPendingActiveCompletedAndEndedEarly() {
        val goalLock = goalLock(
            startDate = LocalDate.of(2026, 6, 4),
            endDate = LocalDate.of(2026, 6, 10),
        )

        assertEquals(
            GoalLockRuntimeStatus.Pending,
            GoalLockPolicy.runtimeStatus(goalLock, LocalDateTime.of(2026, 6, 3, 12, 0)),
        )
        assertEquals(
            GoalLockRuntimeStatus.Active,
            GoalLockPolicy.runtimeStatus(goalLock, LocalDateTime.of(2026, 6, 4, 0, 0)),
        )
        assertEquals(
            GoalLockRuntimeStatus.Completed,
            GoalLockPolicy.runtimeStatus(goalLock, LocalDateTime.of(2026, 6, 11, 0, 0)),
        )
        assertEquals(
            GoalLockRuntimeStatus.EndedEarly,
            GoalLockPolicy.runtimeStatus(
                goalLock.copy(status = GoalLockStoredStatus.EndedEarly),
                LocalDateTime.of(2026, 6, 5, 12, 0),
            ),
        )
    }

    @Test
    fun goalLockRequiresSelectedAppsAndValidDateRange() {
        assertFalse(GoalLockPolicy.isValidForCreation(goalLock(selectedPackages = emptySet())))
        assertFalse(
            GoalLockPolicy.isValidForCreation(
                goalLock(
                    startDate = LocalDate.of(2026, 6, 10),
                    endDate = LocalDate.of(2026, 6, 4),
                ),
            ),
        )
        assertTrue(GoalLockPolicy.isValidForCreation(goalLock()))
    }

    private fun goalLock(
        startDate: LocalDate = LocalDate.of(2026, 6, 1),
        endDate: LocalDate = LocalDate.of(2026, 6, 30),
        lockMode: GoalLockMode = GoalLockMode.AllDay,
        selectedPackages: Set<String> = setOf("com.video.app"),
    ) = GoalLock(
        id = 1L,
        goalName = "SNS 줄이기",
        startDate = startDate,
        endDate = endDate,
        lockMode = lockMode,
        selectedPackages = selectedPackages,
        status = GoalLockStoredStatus.Active,
    )
}

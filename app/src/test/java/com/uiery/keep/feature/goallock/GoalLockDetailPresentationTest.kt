package com.uiery.keep.feature.goallock

import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockRuntimeStatus
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalLockDetailPresentationTest {
    private val today = LocalDate.of(2026, 7, 10)

    @Test
    fun pendingStoredActiveGoalIsEditableAndEndable() {
        val goalLock = goalLock(
            startDate = LocalDate.of(2026, 7, 12),
            endDate = LocalDate.of(2026, 7, 18),
        )

        val presentation = goalLockDetailPresentation(goalLock, today)

        assertEquals(GoalLockRuntimeStatus.Pending, presentation.runtimeStatus)
        assertTrue(presentation.canEdit)
        assertTrue(presentation.canEnd)
        assertEquals(7L, presentation.totalDurationDays)
        assertEquals(GoalLockProgress.Pending(daysUntilStart = 2L), presentation.progress)
    }

    @Test
    fun activeGoalIncludesTodayInRemainingDays() {
        val presentation = goalLockDetailPresentation(
            goalLock(
                startDate = LocalDate.of(2026, 7, 4),
                endDate = LocalDate.of(2026, 7, 15),
            ),
            today,
        )

        assertEquals(GoalLockRuntimeStatus.Active, presentation.runtimeStatus)
        assertTrue(presentation.canEdit)
        assertTrue(presentation.canEnd)
        assertEquals(12L, presentation.totalDurationDays)
        assertEquals(GoalLockProgress.Active(remainingDaysIncludingToday = 6L), presentation.progress)
    }

    @Test
    fun completedGoalIsReadOnlyAndKeepsOriginalRange() {
        val startDate = LocalDate.of(2026, 7, 1)
        val endDate = LocalDate.of(2026, 7, 7)
        val presentation = goalLockDetailPresentation(
            goalLock(
                startDate = startDate,
                endDate = endDate,
                status = GoalLockStoredStatus.Completed,
            ),
            today,
        )

        assertEquals(GoalLockRuntimeStatus.Completed, presentation.runtimeStatus)
        assertFalse(presentation.canEdit)
        assertFalse(presentation.canEnd)
        assertEquals(GoalLockProgress.Completed(startDate, endDate), presentation.progress)
    }

    @Test
    fun endedEarlyGoalIsReadOnly() {
        val presentation = goalLockDetailPresentation(
            goalLock(status = GoalLockStoredStatus.EndedEarly),
            today,
        )

        assertEquals(GoalLockRuntimeStatus.EndedEarly, presentation.runtimeStatus)
        assertFalse(presentation.canEdit)
        assertFalse(presentation.canEnd)
        assertEquals(GoalLockProgress.EndedEarly, presentation.progress)
    }

    private fun goalLock(
        startDate: LocalDate = LocalDate.of(2026, 7, 4),
        endDate: LocalDate = LocalDate.of(2026, 7, 15),
        status: GoalLockStoredStatus = GoalLockStoredStatus.Active,
    ) = GoalLock(
        id = 42L,
        goalName = "시험 준비",
        startDate = startDate,
        endDate = endDate,
        lockMode = GoalLockMode.AllDay,
        selectedPackages = setOf("com.example.app"),
        status = status,
    )
}

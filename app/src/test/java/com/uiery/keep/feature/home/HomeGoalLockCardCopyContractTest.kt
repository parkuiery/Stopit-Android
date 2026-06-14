package com.uiery.keep.feature.home

import com.uiery.keep.R
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGoalLockCardCopyContractTest {
    @Test
    fun pendingActiveCompletedAndEndedEarlyUseDifferentResourceBackedTitles() {
        val commonState = HomeGoalLockCardState(
            goalLockId = 1L,
            goalName = "Exam prep",
            status = HomeGoalLockStatus.Active,
            daysRemaining = 3,
            lockMode = HomeGoalLockCardLockMode.AllDay,
            selectedAppCount = 2,
        )

        assertEquals(
            R.string.home_goal_lock_card_title_pending,
            commonState.copy(status = HomeGoalLockStatus.Pending).displayCopy().titleResId,
        )
        assertEquals(
            R.string.home_goal_lock_card_title_active,
            commonState.copy(status = HomeGoalLockStatus.Active).displayCopy().titleResId,
        )
        assertEquals(
            R.string.home_goal_lock_card_title_completed,
            commonState.copy(status = HomeGoalLockStatus.Completed).displayCopy().titleResId,
        )
        assertEquals(
            R.string.home_goal_lock_card_title_ended_early,
            commonState.copy(status = HomeGoalLockStatus.EndedEarly).displayCopy().titleResId,
        )
    }

    @Test
    fun summaryUsesStatusSpecificResourceAndResourceBackedLockMode() {
        val scheduledPending = HomeGoalLockCardState(
            goalLockId = 1L,
            goalName = "Exam prep",
            status = HomeGoalLockStatus.Pending,
            daysRemaining = 5,
            lockMode = HomeGoalLockCardLockMode.Scheduled,
            selectedAppCount = 4,
        ).displayCopy()

        assertEquals(R.string.home_goal_lock_card_summary_pending, scheduledPending.summaryResId)
        assertEquals(R.string.home_goal_lock_card_lock_mode_scheduled, scheduledPending.lockModeResId)

        val allDayActive = HomeGoalLockCardState(
            goalLockId = 2L,
            goalName = "SNS break",
            status = HomeGoalLockStatus.Active,
            daysRemaining = 2,
            lockMode = HomeGoalLockCardLockMode.AllDay,
            selectedAppCount = 1,
        ).displayCopy()

        assertEquals(R.string.home_goal_lock_card_summary_active, allDayActive.summaryResId)
        assertEquals(R.string.home_goal_lock_card_lock_mode_all_day, allDayActive.lockModeResId)
    }
}

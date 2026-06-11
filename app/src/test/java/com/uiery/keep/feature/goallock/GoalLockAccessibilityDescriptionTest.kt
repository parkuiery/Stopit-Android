package com.uiery.keep.feature.goallock

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalLockAccessibilityDescriptionTest {
    @Test
    fun creationDescriptionSummarizesGoalDurationModeAndSelectedApps() {
        val description = buildGoalLockCreationAccessibilityDescription(
            goalName = "시험 준비",
            durationRangeText = "2026-06-04 - 2026-07-03",
            lockModeText = "All day lock",
            selectedAppsText = "1 selected app will be used for this goal lock.",
        )

        assertEquals(
            "시험 준비, 2026-06-04 - 2026-07-03, All day lock, 1 selected app will be used for this goal lock.",
            description,
        )
    }

    @Test
    fun detailDescriptionSummarizesGoalSummaryAndRuntimeStatus() {
        val description = buildGoalLockDetailAccessibilityDescription(
            goalName = "시험 준비",
            summaryText = "All day lock · 2 apps",
            statusText = "Active",
        )

        assertEquals(
            "시험 준비, All day lock · 2 apps, Active",
            description,
        )
    }
}

package com.uiery.keep

import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionSurface
import com.uiery.keep.feature.routine.RoutineRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityTest {
    @Test
    fun repeatBlockRoutineRouteRequiresCompletePostBlockPrefillExtras() {
        assertNull(
            createRepeatBlockRoutineRoute(
                repeatBlockSurface = RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
                repeatBlockReason = "rapid_retry",
                repeatBlockTimeBucket = "night",
                repeatBlockDayType = "daily",
                repeatBlockCategoryBucket = "social",
                repeatBlockCountBucket = "3_5",
                repeatBlockCoverageState = "not_covered",
                prefillPackages = emptyList(),
                prefillStartHour = 23,
                prefillStartMinute = 0,
                prefillEndHour = 0,
                prefillEndMinute = 0,
            ),
        )
    }

    @Test
    fun repeatBlockRoutineRoutePreservesPostBlockPrefillExtras() {
        val route = createRepeatBlockRoutineRoute(
            repeatBlockSurface = RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
            repeatBlockReason = "rapid_retry",
            repeatBlockTimeBucket = "night",
            repeatBlockDayType = "daily",
            repeatBlockCategoryBucket = "social",
            repeatBlockCountBucket = "3_5",
            repeatBlockCoverageState = "not_covered",
            prefillPackages = listOf("com.instagram.android"),
            prefillStartHour = 23,
            prefillStartMinute = 0,
            prefillEndHour = 0,
            prefillEndMinute = 0,
        )

        assertEquals(
            RoutineRoute(
                repeatBlockSurface = RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
                repeatBlockReason = "rapid_retry",
                repeatBlockTimeBucket = "night",
                repeatBlockDayType = "daily",
                repeatBlockCategoryBucket = "social",
                repeatBlockCountBucket = "3_5",
                repeatBlockCoverageState = "not_covered",
                prefillPackages = listOf("com.instagram.android"),
                prefillStartHour = 23,
                prefillStartMinute = 0,
                prefillEndHour = 0,
                prefillEndMinute = 0,
            ),
            route,
        )
    }
}

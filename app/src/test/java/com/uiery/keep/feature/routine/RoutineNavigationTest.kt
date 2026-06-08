package com.uiery.keep.feature.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineNavigationTest {
    @Test
    fun repeatBlockRouteRestoresPrivacySafeRoutinePrefillSuggestion() {
        val route = RoutineRoute(
            repeatBlockSurface = "home",
            repeatBlockReason = "repeat_block_time_bucket",
            repeatBlockTimeBucket = "night",
            repeatBlockDayType = "weekday",
            repeatBlockCategoryBucket = "social",
            repeatBlockCountBucket = "3_5",
            repeatBlockCoverageState = "not_covered",
            prefillPackages = listOf("com.instagram.android", "com.twitter.android"),
            prefillStartHour = 22,
            prefillStartMinute = 0,
            prefillEndHour = 0,
            prefillEndMinute = 0,
        )

        val suggestion = route.toRepeatBlockRoutineSuggestionOrNull()

        requireNotNull(suggestion)
        assertEquals(RepeatBlockSuggestionReason.RepeatBlockTimeBucket, suggestion.reason)
        assertEquals(RepeatBlockTimeBucket.Night, suggestion.timeBucket)
        assertEquals(RepeatBlockDayType.Weekday, suggestion.dayType)
        assertEquals(RepeatBlockCategoryBucket.Social, suggestion.categoryBucket)
        assertEquals(RepeatBlockCountBucket.ThreeToFive, suggestion.repeatCountBucket)
        assertEquals(RoutineCoverageState.NotCovered, suggestion.routineCoverageState)
        assertEquals(listOf("com.instagram.android", "com.twitter.android"), suggestion.prefillPackages)
        assertEquals(22, suggestion.prefillStartTime.hour)
        assertEquals(0, suggestion.prefillEndTime.hour)
    }

    @Test
    fun repeatBlockRouteRejectsIncompletePrefillSuggestion() {
        val route = RoutineRoute(
            repeatBlockSurface = "home",
            repeatBlockReason = "repeat_block_time_bucket",
            repeatBlockTimeBucket = "night",
            repeatBlockDayType = "weekday",
            repeatBlockCategoryBucket = "social",
            repeatBlockCountBucket = "3_5",
            repeatBlockCoverageState = "not_covered",
            prefillPackages = emptyList(),
            prefillStartHour = 22,
            prefillStartMinute = 0,
            prefillEndHour = 0,
            prefillEndMinute = 0,
        )

        assertNull(route.toRepeatBlockRoutineSuggestionOrNull())
    }
}

package com.uiery.keep.feature.routine

import com.uiery.keep.model.RoutineModel
import java.time.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatBlockRoutineSuggestionPolicyTest {
    @Test
    fun repeatedNightSocialBlocksCreateOneEditableRoutineSuggestion() {
        val now = LocalDateTime.of(2026, 6, 6, 12, 0)
        val histories = listOf(
            history("2026-06-05T23:20:00", "com.instagram.android"),
            history("2026-06-04T23:05:00", "com.twitter.android"),
            history("2026-06-03T22:45:00", "com.instagram.android"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = emptyList(),
            dismissedSuggestions = emptyList(),
            now = now,
        )

        requireNotNull(suggestion)
        assertEquals(RepeatBlockTimeBucket.Night, suggestion.timeBucket)
        assertEquals(RepeatBlockDayType.Weekday, suggestion.dayType)
        assertEquals(RepeatBlockCategoryBucket.Social, suggestion.categoryBucket)
        assertEquals(RepeatBlockCountBucket.ThreeToFive, suggestion.repeatCountBucket)
        assertEquals(RoutineCoverageState.NotCovered, suggestion.routineCoverageState)
        assertEquals(RepeatBlockSuggestionReason.RepeatBlockTimeBucket, suggestion.reason)
        assertEquals(listOf("com.instagram.android", "com.twitter.android"), suggestion.prefillPackages)
        assertEquals(22, suggestion.prefillStartTime.hour)
        assertEquals(0, suggestion.prefillStartTime.minute)
        assertEquals(0, suggestion.prefillEndTime.hour)
        assertEquals(0, suggestion.prefillEndTime.minute)
    }

    @Test
    fun activeRoutineCoveringSameAppsAndTimeSuppressesSuggestion() {
        val histories = listOf(
            history("2026-06-05T23:20:00", "com.instagram.android"),
            history("2026-06-04T23:05:00", "com.instagram.android"),
            history("2026-06-03T22:45:00", "com.instagram.android"),
        )
        val routine = routine(
            repeatDays = "1111111",
            startTime = LocalTime(22, 0),
            endTime = LocalTime(0, 0),
            apps = listOf("com.instagram.android"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = listOf(routine),
            dismissedSuggestions = emptyList(),
            now = LocalDateTime.of(2026, 6, 6, 12, 0),
        )

        assertNull(suggestion)
    }

    @Test
    fun dismissedSuggestionIsSuppressedForSevenDays() {
        val now = LocalDateTime.of(2026, 6, 6, 12, 0)
        val histories = listOf(
            history("2026-06-05T23:20:00", "com.instagram.android"),
            history("2026-06-04T23:05:00", "com.twitter.android"),
            history("2026-06-03T22:45:00", "com.instagram.android"),
        )
        val dismissed = RepeatBlockDismissedSuggestion(
            timeBucket = RepeatBlockTimeBucket.Night,
            dayType = RepeatBlockDayType.Weekday,
            categoryBucket = RepeatBlockCategoryBucket.Social,
            dismissedAt = LocalDateTime.of(2026, 6, 1, 9, 0),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = emptyList(),
            dismissedSuggestions = listOf(dismissed),
            now = now,
        )

        assertNull(suggestion)
    }

    @Test
    fun onlyMostRecentHighestValueCandidateIsReturned() {
        val now = LocalDateTime.of(2026, 6, 6, 12, 0)
        val histories = listOf(
            history("2026-06-05T23:20:00", "com.youtube.android"),
            history("2026-06-04T23:05:00", "com.youtube.android"),
            history("2026-06-03T22:45:00", "com.netflix.mediaclient"),
            history("2026-06-02T19:10:00", "com.instagram.android"),
            history("2026-06-01T19:05:00", "com.instagram.android"),
            history("2026-05-31T19:00:00", "com.twitter.android"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = emptyList(),
            dismissedSuggestions = emptyList(),
            now = now,
        )

        requireNotNull(suggestion)
        assertEquals(RepeatBlockTimeBucket.Night, suggestion.timeBucket)
        assertEquals(RepeatBlockCategoryBucket.Video, suggestion.categoryBucket)
        assertEquals(listOf("com.youtube.android", "com.netflix.mediaclient"), suggestion.prefillPackages)
    }

    private fun history(start: String, packageName: String): RepeatBlockHistorySample =
        RepeatBlockHistorySample(
            startDateTime = LocalDateTime.parse(start),
            blockedPackages = listOf(packageName),
        )

    private fun routine(
        repeatDays: String,
        startTime: LocalTime,
        endTime: LocalTime,
        apps: List<String>,
    ): RoutineModel = RoutineModel(
        id = 1,
        name = "Night routine",
        startTime = startTime,
        endTime = endTime,
        repeatDays = repeatDays,
        lockApplications = apps,
        isEnabled = true,
    )
}

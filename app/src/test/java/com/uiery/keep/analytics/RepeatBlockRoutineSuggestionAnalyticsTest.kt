package com.uiery.keep.analytics

import com.uiery.keep.feature.routine.RepeatBlockCategoryBucket
import com.uiery.keep.feature.routine.RepeatBlockCountBucket
import com.uiery.keep.feature.routine.RepeatBlockDayType
import com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestion
import com.uiery.keep.feature.routine.RepeatBlockSuggestionReason
import com.uiery.keep.feature.routine.RepeatBlockTimeBucket
import com.uiery.keep.feature.routine.RoutineCoverageState
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RepeatBlockRoutineSuggestionAnalyticsTest {
    private val backend = RecordingRepeatBlockAnalyticsBackend()
    private val analytics = FirebaseKeepAnalytics(backend)

    @Test
    fun repeatBlockRoutineSuggestionEventsUseOnlyPrivacySafeBuckets() {
        val suggestion = RepeatBlockRoutineSuggestion(
            timeBucket = RepeatBlockTimeBucket.Night,
            dayType = RepeatBlockDayType.Weekday,
            categoryBucket = RepeatBlockCategoryBucket.Social,
            repeatCountBucket = RepeatBlockCountBucket.ThreeToFive,
            routineCoverageState = RoutineCoverageState.NotCovered,
            reason = RepeatBlockSuggestionReason.RepeatBlockTimeBucket,
            prefillPackages = listOf("com.instagram.android", "com.twitter.android"),
            prefillStartTime = LocalTime(22, 0),
            prefillEndTime = LocalTime(0, 0),
        )

        analytics.trackRepeatBlockRoutineSuggestionShown(
            surface = RepeatBlockRoutineSuggestionSurface.HOME,
            suggestion = suggestion,
        )
        analytics.trackRepeatBlockRoutineSuggestionClicked(
            surface = RepeatBlockRoutineSuggestionSurface.HOME,
            suggestion = suggestion,
        )
        analytics.trackRepeatBlockRoutineSuggestionDismissed(
            surface = RepeatBlockRoutineSuggestionSurface.HOME,
            suggestion = suggestion,
        )
        analytics.trackRepeatBlockRoutineSuggestionApplied(
            surface = RepeatBlockRoutineSuggestionSurface.HOME,
            suggestion = suggestion,
        )

        assertEquals(
            listOf(
                KeepAnalyticsEvent.REPEAT_BLOCK_ROUTINE_SUGGESTION_SHOWN,
                KeepAnalyticsEvent.REPEAT_BLOCK_ROUTINE_SUGGESTION_CLICKED,
                KeepAnalyticsEvent.REPEAT_BLOCK_ROUTINE_SUGGESTION_DISMISSED,
                KeepAnalyticsEvent.REPEAT_BLOCK_ROUTINE_SUGGESTION_APPLIED,
            ),
            backend.loggedEvents.map { it.name },
        )
        backend.loggedEvents.forEach { event ->
            assertEquals(
                mapOf(
                    KeepAnalyticsParam.SURFACE to RepeatBlockRoutineSuggestionSurface.HOME,
                    KeepAnalyticsParam.SUGGESTION_REASON to "repeat_block_time_bucket",
                    KeepAnalyticsParam.TIME_BUCKET to "night",
                    KeepAnalyticsParam.DAY_TYPE to "weekday",
                    KeepAnalyticsParam.CATEGORY_BUCKET to "social",
                    KeepAnalyticsParam.REPEAT_COUNT_BUCKET to "3_5",
                    KeepAnalyticsParam.ROUTINE_COVERAGE_STATE to "not_covered",
                    KeepAnalyticsParam.SUGGESTION_VARIANT to RepeatBlockSuggestionVariant.DEFAULT,
                ),
                event.params,
            )
            assertFalse(event.params.containsKey(KeepAnalyticsParam.BLOCKED_APP_PACKAGE))
            assertFalse(event.params.containsKey(KeepAnalyticsParam.ROUTINE_ID))
        }
    }
}

private data class LoggedRepeatBlockAnalyticsEvent(
    val name: String,
    val params: Map<String, Any?>,
)

private class RecordingRepeatBlockAnalyticsBackend : AnalyticsBackend {
    val loggedEvents = mutableListOf<LoggedRepeatBlockAnalyticsEvent>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        loggedEvents += LoggedRepeatBlockAnalyticsEvent(name = name, params = params)
    }

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(
        name: String,
        value: String,
    ) = Unit
}

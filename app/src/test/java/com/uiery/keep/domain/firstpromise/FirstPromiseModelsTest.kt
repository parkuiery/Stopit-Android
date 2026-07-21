package com.uiery.keep.domain.firstpromise

import com.uiery.keep.analytics.routine.RoutineSavedScheduleState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstPromiseModelsTest {
    @Test
    fun analyticsFacingEnumsUseTheApprovedWireValues() {
        assertEquals(
            mapOf(
                FirstPromiseGoal.Sleep to "sleep",
                FirstPromiseGoal.Focus to "focus",
                FirstPromiseGoal.Study to "study",
                FirstPromiseGoal.FreeTime to "free_time",
                FirstPromiseGoal.Unspecified to "unspecified",
            ),
            FirstPromiseGoal.entries.associateWith(FirstPromiseGoal::analyticsValue),
        )
        assertEquals(
            mapOf(
                FirstPromiseSource.Personalized to "personalized",
                FirstPromiseSource.GoalTemplate to "goal_template",
                FirstPromiseSource.Manual to "manual",
            ),
            FirstPromiseSource.entries.associateWith(FirstPromiseSource::analyticsValue),
        )
        assertEquals(
            mapOf(
                FirstPromiseOrigin.FirstPromiseRoutine to "first_promise_routine",
                FirstPromiseOrigin.FirstPromisePractice to "first_promise_practice",
            ),
            FirstPromiseOrigin.entries.associateWith(FirstPromiseOrigin::analyticsValue),
        )
        assertEquals(
            mapOf(
                UsageDataQuality.Full to "full",
                UsageDataQuality.UsageOnly to "usage_only",
                UsageDataQuality.Insufficient to "insufficient",
            ),
            UsageDataQuality.entries.associateWith(UsageDataQuality::analyticsValue),
        )
        assertEquals(
            mapOf(
                UsagePatternType.Night to "night",
                UsagePatternType.PeakWindow to "peak_window",
                UsagePatternType.TopApp to "top_app",
                UsagePatternType.Manual to "manual",
            ),
            UsagePatternType.entries.associateWith(UsagePatternType::analyticsValue),
        )
        assertEquals(
            mapOf(
                UsageCoverageBucket.Zero to "0",
                UsageCoverageBucket.OneTwo to "1_2",
                UsageCoverageBucket.ThreeSix to "3_6",
                UsageCoverageBucket.Seven to "7",
            ),
            UsageCoverageBucket.entries.associateWith(UsageCoverageBucket::analyticsValue),
        )
        assertEquals(
            mapOf(
                AnalysisLatencyBucket.UnderOneSecond to "under_1s",
                AnalysisLatencyBucket.OneToThreeSeconds to "1_3s",
                AnalysisLatencyBucket.ThreeToFiveSeconds to "3_5s",
                AnalysisLatencyBucket.Timeout to "timeout",
            ),
            AnalysisLatencyBucket.entries.associateWith(AnalysisLatencyBucket::analyticsValue),
        )
        assertEquals(
            mapOf(
                PromiseEditField.App to "app",
                PromiseEditField.StartTime to "start_time",
                PromiseEditField.RepeatDays to "repeat_days",
            ),
            PromiseEditField.entries.associateWith(PromiseEditField::analyticsValue),
        )
        assertEquals(
            mapOf(OnboardingAssignmentVersion.V1 to "v1"),
            OnboardingAssignmentVersion.entries.associateWith(OnboardingAssignmentVersion::analyticsValue),
        )
        assertEquals(
            mapOf(
                FirstPromisePracticeOutcome.Started to "started",
                FirstPromisePracticeOutcome.Skipped to "skipped",
                FirstPromisePracticeOutcome.StartFailed to "start_failed",
            ),
            FirstPromisePracticeOutcome.entries.associateWith(FirstPromisePracticeOutcome::analyticsValue),
        )
        assertEquals(
            mapOf(
                OnboardingVariant.Control to "control",
                OnboardingVariant.PromiseCoachV1 to "promise_coach_v1",
            ),
            OnboardingVariant.entries.associateWith(OnboardingVariant::analyticsValue),
        )
    }

    @Test
    fun firstPromiseScheduleStateMatchesTheCanonicalRoutineScheduleState() {
        assertEquals(RoutineSavedScheduleState.ENABLED, FirstPromiseScheduleState.Enabled.analyticsValue)
        assertEquals(
            RoutineSavedScheduleState.DISABLED_EXACT_ALARM_MISSING,
            FirstPromiseScheduleState.DisabledExactAlarmMissing.analyticsValue,
        )
        assertEquals(
            RoutineSavedScheduleState.DISABLED_USER_CHOICE,
            FirstPromiseScheduleState.DisabledUserChoice.analyticsValue,
        )
        assertEquals(
            RoutineSavedScheduleState.DISABLED_UNKNOWN,
            FirstPromiseScheduleState.DisabledUnknown.analyticsValue,
        )
    }

    @Test
    fun firstPromiseDraftRoundTripsWithTypedScheduleInputs() {
        val draft = FirstPromiseDraft(
            draftId = "local-draft",
            goal = FirstPromiseGoal.Sleep,
            packageName = "com.example.video",
            appLabel = "Video",
            startMinutes = 22 * 60,
            repeatDays = setOf(1, 3, 5),
            source = FirstPromiseSource.Personalized,
        )

        val encoded = Json.encodeToString(FirstPromiseDraft.serializer(), draft)

        assertEquals(draft, Json.decodeFromString(FirstPromiseDraft.serializer(), encoded))
    }

    @Test
    fun recommendationReasonsKeepOnlyTypedCategoricalPatternMetadata() {
        val observed = RecommendationReason.ObservedPeak(
            patternType = UsagePatternType.Night,
            usageCoverageDays = 7,
            eventCoverageDays = 6,
            startMinutes = 22 * 60,
        )
        val goalDefault = RecommendationReason.GoalDefault(
            goal = FirstPromiseGoal.Focus,
            startMinutes = 9 * 60,
            usageCoverageDays = 4,
            eventCoverageDays = 2,
        )

        assertEquals(UsagePatternType.Night, observed.patternType)
        assertEquals(UsagePatternType.TopApp, goalDefault.patternType)
        assertEquals(UsagePatternType.Manual, RecommendationReason.Manual.patternType)
    }
}

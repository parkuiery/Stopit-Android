package com.uiery.keep.domain.firstpromise

import kotlinx.serialization.Serializable

@Serializable
enum class FirstPromiseGoal(val analyticsValue: String) {
    Sleep("sleep"),
    Focus("focus"),
    Study("study"),
    FreeTime("free_time"),
    Unspecified("unspecified"),
}

@Serializable
enum class FirstPromiseSource(val analyticsValue: String) {
    Personalized("personalized"),
    GoalTemplate("goal_template"),
    Manual("manual"),
}

@Serializable
enum class FirstPromiseOrigin(val analyticsValue: String) {
    FirstPromiseRoutine("first_promise_routine"),
    FirstPromisePractice("first_promise_practice"),
}

@Serializable
enum class UsageDataQuality(val analyticsValue: String) {
    Full("full"),
    UsageOnly("usage_only"),
    Insufficient("insufficient"),
}

@Serializable
enum class UsagePatternType(val analyticsValue: String) {
    Night("night"),
    PeakWindow("peak_window"),
    TopApp("top_app"),
    Manual("manual"),
}

@Serializable
enum class UsageCoverageBucket(val analyticsValue: String) {
    Zero("0"),
    OneTwo("1_2"),
    ThreeSix("3_6"),
    Seven("7"),
}

@Serializable
enum class AnalysisLatencyBucket(val analyticsValue: String) {
    UnderOneSecond("under_1s"),
    OneToThreeSeconds("1_3s"),
    ThreeToFiveSeconds("3_5s"),
    Timeout("timeout"),
}

@Serializable
enum class PromiseEditField(val analyticsValue: String) {
    App("app"),
    StartTime("start_time"),
    RepeatDays("repeat_days"),
}

@Serializable
enum class FirstPromisePracticeOutcome(val analyticsValue: String) {
    Started("started"),
    Skipped("skipped"),
    StartFailed("start_failed"),
}

@Serializable
enum class OnboardingAssignmentVersion(val analyticsValue: String) {
    V1("v1"),
}

@Serializable
enum class FirstPromiseScheduleState(val analyticsValue: String) {
    Enabled("enabled"),
    DisabledExactAlarmMissing("disabled_exact_alarm_missing"),
    DisabledUserChoice("disabled_user_choice"),
    DisabledUnknown("disabled_unknown"),
}

@Serializable
enum class OnboardingVariant(val analyticsValue: String) {
    Control("control"),
    PromiseCoachV1("promise_coach_v1"),
}

@Serializable
data class FirstPromiseDraft(
    val draftId: String,
    val goal: FirstPromiseGoal,
    val packageName: String,
    val appLabel: String,
    val startMinutes: Int,
    val repeatDays: Set<Int>,
    val source: FirstPromiseSource,
)

sealed interface RecommendationReason {
    val patternType: UsagePatternType

    data class ObservedPeak(
        override val patternType: UsagePatternType,
        val usageCoverageDays: Int,
        val eventCoverageDays: Int,
        val startMinutes: Int,
    ) : RecommendationReason

    data class GoalDefault(
        override val patternType: UsagePatternType = UsagePatternType.TopApp,
        val goal: FirstPromiseGoal,
        val startMinutes: Int,
        val usageCoverageDays: Int,
    ) : RecommendationReason

    data object Manual : RecommendationReason {
        override val patternType = UsagePatternType.Manual
    }
}

data class FirstPromiseProposal(
    val draft: FirstPromiseDraft,
    val reason: RecommendationReason,
)

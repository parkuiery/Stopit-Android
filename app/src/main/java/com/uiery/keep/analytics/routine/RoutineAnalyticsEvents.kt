package com.uiery.keep.analytics.routine

import com.uiery.keep.analytics.AnalyticsEvent
import com.uiery.keep.analytics.KeepAnalyticsParam

object RoutineAnalyticsEvents {
    fun templateShareTapped(
        templateCategory: String,
        repeatDaysBucket: String,
        timeWindowBucket: String,
        routineNameIncluded: Boolean,
    ) = AnalyticsEvent(
        name = RoutineAnalyticsEvent.ROUTINE_TEMPLATE_SHARE_TAPPED,
        params = routineTemplateShareParams(
            templateCategory = templateCategory,
            repeatDaysBucket = repeatDaysBucket,
            timeWindowBucket = timeWindowBucket,
            routineNameIncluded = routineNameIncluded,
        ),
    )

    fun templateShareSheetOpened(
        templateCategory: String,
        repeatDaysBucket: String,
        timeWindowBucket: String,
        routineNameIncluded: Boolean,
    ) = AnalyticsEvent(
        name = RoutineAnalyticsEvent.ROUTINE_TEMPLATE_SHARE_SHEET_OPENED,
        params = routineTemplateShareParams(
            templateCategory = templateCategory,
            repeatDaysBucket = repeatDaysBucket,
            timeWindowBucket = timeWindowBucket,
            routineNameIncluded = routineNameIncluded,
        ),
    )

    fun templateShareFailed(
        templateCategory: String,
        reason: String,
    ) = AnalyticsEvent(
        name = RoutineAnalyticsEvent.ROUTINE_TEMPLATE_SHARE_FAILED,
        params = mapOf(
            RoutineAnalyticsParam.TEMPLATE_CATEGORY to templateCategory,
            KeepAnalyticsParam.REASON to reason,
        ),
    )

    fun repeatBlockSuggestionShown(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) = repeatBlockSuggestionEvent(
        name = RoutineAnalyticsEvent.REPEAT_BLOCK_ROUTINE_SUGGESTION_SHOWN,
        surface = surface,
        suggestion = suggestion,
    )

    fun repeatBlockSuggestionClicked(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) = repeatBlockSuggestionEvent(
        name = RoutineAnalyticsEvent.REPEAT_BLOCK_ROUTINE_SUGGESTION_CLICKED,
        surface = surface,
        suggestion = suggestion,
    )

    fun repeatBlockSuggestionDismissed(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) = repeatBlockSuggestionEvent(
        name = RoutineAnalyticsEvent.REPEAT_BLOCK_ROUTINE_SUGGESTION_DISMISSED,
        surface = surface,
        suggestion = suggestion,
    )

    fun repeatBlockSuggestionApplied(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) = repeatBlockSuggestionEvent(
        name = RoutineAnalyticsEvent.REPEAT_BLOCK_ROUTINE_SUGGESTION_APPLIED,
        surface = surface,
        suggestion = suggestion,
    )

    private fun routineTemplateShareParams(
        templateCategory: String,
        repeatDaysBucket: String,
        timeWindowBucket: String,
        routineNameIncluded: Boolean,
    ) = mapOf(
        RoutineAnalyticsParam.TEMPLATE_CATEGORY to templateCategory,
        RoutineAnalyticsParam.REPEAT_DAYS_BUCKET to repeatDaysBucket,
        RoutineAnalyticsParam.TIME_WINDOW_BUCKET to timeWindowBucket,
        RoutineAnalyticsParam.ROUTINE_NAME_INCLUDED to routineNameIncluded,
    )

    private fun repeatBlockSuggestionEvent(
        name: String,
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) = AnalyticsEvent(
        name = name,
        params = mapOf(
            KeepAnalyticsParam.SURFACE to surface,
            RoutineAnalyticsParam.SUGGESTION_REASON to suggestion.reason,
            RoutineAnalyticsParam.TIME_BUCKET to suggestion.timeBucket,
            RoutineAnalyticsParam.DAY_TYPE to suggestion.dayType,
            RoutineAnalyticsParam.CATEGORY_BUCKET to suggestion.categoryBucket,
            RoutineAnalyticsParam.REPEAT_COUNT_BUCKET to suggestion.repeatCountBucket,
            RoutineAnalyticsParam.ROUTINE_COVERAGE_STATE to suggestion.routineCoverageState,
            RoutineAnalyticsParam.SUGGESTION_VARIANT to RepeatBlockSuggestionVariant.DEFAULT,
        ),
    )
}

data class RepeatBlockRoutineSuggestionAnalyticsPayload(
    val reason: String,
    val timeBucket: String,
    val dayType: String,
    val categoryBucket: String,
    val repeatCountBucket: String,
    val routineCoverageState: String,
)

object RoutineAnalyticsEvent {
    const val ROUTINE_TEMPLATE_SHARE_TAPPED = "routine_template_share_tapped"
    const val ROUTINE_TEMPLATE_SHARE_SHEET_OPENED = "routine_template_share_sheet_opened"
    const val ROUTINE_TEMPLATE_SHARE_FAILED = "routine_template_share_failed"
    const val REPEAT_BLOCK_ROUTINE_SUGGESTION_SHOWN = "repeat_block_routine_suggestion_shown"
    const val REPEAT_BLOCK_ROUTINE_SUGGESTION_CLICKED = "repeat_block_routine_suggestion_clicked"
    const val REPEAT_BLOCK_ROUTINE_SUGGESTION_DISMISSED = "repeat_block_routine_suggestion_dismissed"
    const val REPEAT_BLOCK_ROUTINE_SUGGESTION_APPLIED = "repeat_block_routine_suggestion_applied"
}

object RoutineAnalyticsParam {
    const val TEMPLATE_CATEGORY = "template_category"
    const val REPEAT_DAYS_BUCKET = "repeat_days_bucket"
    const val TIME_WINDOW_BUCKET = "time_window_bucket"
    const val ROUTINE_NAME_INCLUDED = "routine_name_included"
    const val SUGGESTION_REASON = "suggestion_reason"
    const val TIME_BUCKET = "time_bucket"
    const val DAY_TYPE = "day_type"
    const val CATEGORY_BUCKET = "category_bucket"
    const val REPEAT_COUNT_BUCKET = "repeat_count_bucket"
    const val ROUTINE_COVERAGE_STATE = "routine_coverage_state"
    const val SUGGESTION_VARIANT = "suggestion_variant"
}

object RoutineTemplateCategoryName {
    const val STUDY = "study"
    const val WORK = "work"
    const val NIGHT_FOCUS = "night_focus"
    const val CUSTOM = "custom"
}

object RoutineTemplateRepeatDaysBucketName {
    const val WEEKDAY = "weekday"
    const val WEEKEND = "weekend"
    const val DAILY = "daily"
    const val CUSTOM_DAYS = "custom_days"
    const val NONE = "none"
}

object RoutineTemplateTimeWindowBucketName {
    const val MORNING = "morning"
    const val AFTERNOON = "afternoon"
    const val EVENING = "evening"
    const val NIGHT = "night"
    const val OVERNIGHT = "overnight"
    const val CUSTOM_WINDOW = "custom_window"
}

object RoutineTemplateShareFailureReason {
    const val ACTIVITY_NOT_FOUND = "activity_not_found"
    const val INVALID_TEMPLATE = "invalid_template"
}

object RepeatBlockRoutineSuggestionSurface {
    const val HOME = "home"
}

object RepeatBlockSuggestionVariant {
    const val DEFAULT = "default"
}

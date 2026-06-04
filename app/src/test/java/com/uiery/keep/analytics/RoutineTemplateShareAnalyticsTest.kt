package com.uiery.keep.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineTemplateShareAnalyticsTest {
    private val backend = RecordingAnalyticsBackend()
    private val analytics = FirebaseKeepAnalytics(backend)

    @Test
    fun routineTemplateShareEventsUseSafeBucketedParamsOnly() {
        analytics.trackRoutineTemplateShareTapped(
            templateCategory = RoutineTemplateCategoryName.STUDY,
            repeatDaysBucket = RoutineTemplateRepeatDaysBucketName.WEEKDAY,
            timeWindowBucket = RoutineTemplateTimeWindowBucketName.EVENING,
            routineNameIncluded = false,
        )
        analytics.trackRoutineTemplateShareSheetOpened(
            templateCategory = RoutineTemplateCategoryName.STUDY,
            repeatDaysBucket = RoutineTemplateRepeatDaysBucketName.WEEKDAY,
            timeWindowBucket = RoutineTemplateTimeWindowBucketName.EVENING,
            routineNameIncluded = false,
        )
        analytics.trackRoutineTemplateShareFailed(
            templateCategory = RoutineTemplateCategoryName.CUSTOM,
            reason = RoutineTemplateShareFailureReason.INVALID_TEMPLATE,
        )

        assertEquals(
            listOf(
                LoggedAnalyticsEvent(
                    name = KeepAnalyticsEvent.ROUTINE_TEMPLATE_SHARE_TAPPED,
                    params = mapOf(
                        KeepAnalyticsParam.TEMPLATE_CATEGORY to RoutineTemplateCategoryName.STUDY,
                        KeepAnalyticsParam.REPEAT_DAYS_BUCKET to RoutineTemplateRepeatDaysBucketName.WEEKDAY,
                        KeepAnalyticsParam.TIME_WINDOW_BUCKET to RoutineTemplateTimeWindowBucketName.EVENING,
                        KeepAnalyticsParam.ROUTINE_NAME_INCLUDED to false,
                    ),
                ),
                LoggedAnalyticsEvent(
                    name = KeepAnalyticsEvent.ROUTINE_TEMPLATE_SHARE_SHEET_OPENED,
                    params = mapOf(
                        KeepAnalyticsParam.TEMPLATE_CATEGORY to RoutineTemplateCategoryName.STUDY,
                        KeepAnalyticsParam.REPEAT_DAYS_BUCKET to RoutineTemplateRepeatDaysBucketName.WEEKDAY,
                        KeepAnalyticsParam.TIME_WINDOW_BUCKET to RoutineTemplateTimeWindowBucketName.EVENING,
                        KeepAnalyticsParam.ROUTINE_NAME_INCLUDED to false,
                    ),
                ),
                LoggedAnalyticsEvent(
                    name = KeepAnalyticsEvent.ROUTINE_TEMPLATE_SHARE_FAILED,
                    params = mapOf(
                        KeepAnalyticsParam.TEMPLATE_CATEGORY to RoutineTemplateCategoryName.CUSTOM,
                        KeepAnalyticsParam.REASON to RoutineTemplateShareFailureReason.INVALID_TEMPLATE,
                    ),
                ),
            ),
            backend.loggedEvents,
        )
    }
}

private data class LoggedAnalyticsEvent(
    val name: String,
    val params: Map<String, Any?>,
)

private class RecordingAnalyticsBackend : AnalyticsBackend {
    val loggedEvents = mutableListOf<LoggedAnalyticsEvent>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        loggedEvents += LoggedAnalyticsEvent(name = name, params = params)
    }

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(
        name: String,
        value: String,
    ) = Unit
}

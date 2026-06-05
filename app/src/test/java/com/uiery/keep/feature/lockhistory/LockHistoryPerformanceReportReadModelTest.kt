package com.uiery.keep.feature.lockhistory

import com.uiery.keep.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LockHistoryPerformanceReportReadModelTest {
    @Test
    fun emptyWeeklyHistoryUsesEncouragingStartCopyWithoutTopApps() {
        val report = buildLockHistoryPerformanceReport(
            periodType = PeriodType.WEEK,
            totalDurationMillis = 0L,
            sessionCount = 0,
            topApps = emptyList(),
        )

        assertEquals(LockHistoryPerformanceReportState.EMPTY, report.state)
        assertEquals("week", report.periodTypeAnalyticsValue)
        assertEquals(R.string.lock_history_performance_empty_week_headline, report.headlineResId)
        assertEquals(R.string.lock_history_performance_empty_supporting, report.supportingResId)
        assertEquals(R.string.lock_history_top_apps_empty_supporting, report.topAppsSupportingResId)
        assertFalse(report.shouldShowTopApps)
    }

    @Test
    fun singleShortSessionIsLowDataAndRecognizesTheFirstRecord() {
        val report = buildLockHistoryPerformanceReport(
            periodType = PeriodType.WEEK,
            totalDurationMillis = 2 * 60 * 1000L,
            sessionCount = 1,
            topApps = listOf("com.example.video" to 1),
        )

        assertEquals(LockHistoryPerformanceReportState.LOW_DATA, report.state)
        assertEquals(R.string.lock_history_performance_low_data_headline, report.headlineResId)
        assertEquals(R.string.lock_history_performance_low_data_supporting, report.supportingResId)
        assertEquals(R.string.lock_history_top_apps_positive_supporting, report.topAppsSupportingResId)
        assertEquals("1", report.sessionCountBucket)
        assertEquals("1_29", report.durationMinutesBucket)
        assertEquals("1", report.topAppsCountBucket)
    }

    @Test
    fun weeklyHistoryUsesWeeklyAchievementHeadlineAndPrivacySafeBuckets() {
        val report = buildLockHistoryPerformanceReport(
            periodType = PeriodType.WEEK,
            totalDurationMillis = 140 * 60 * 1000L,
            sessionCount = 5,
            topApps = listOf(
                "com.example.video" to 3,
                "com.example.social" to 2,
            ),
        )

        assertEquals(LockHistoryPerformanceReportState.HAS_HISTORY, report.state)
        assertEquals(R.string.lock_history_performance_week_headline, report.headlineResId)
        assertEquals(R.string.lock_history_performance_sessions_supporting, report.supportingResId)
        assertEquals("4_6", report.sessionCountBucket)
        assertEquals("120_239", report.durationMinutesBucket)
        assertEquals("2_3", report.topAppsCountBucket)
    }

    @Test
    fun monthlyHistoryUsesMonthlyAchievementHeadline() {
        val report = buildLockHistoryPerformanceReport(
            periodType = PeriodType.MONTH,
            totalDurationMillis = 65 * 60 * 1000L,
            sessionCount = 2,
            topApps = listOf("com.example.video" to 2),
        )

        assertEquals(LockHistoryPerformanceReportState.HAS_HISTORY, report.state)
        assertEquals("month", report.periodTypeAnalyticsValue)
        assertEquals(R.string.lock_history_performance_month_headline, report.headlineResId)
        assertEquals("2_3", report.sessionCountBucket)
        assertEquals("60_119", report.durationMinutesBucket)
    }
}

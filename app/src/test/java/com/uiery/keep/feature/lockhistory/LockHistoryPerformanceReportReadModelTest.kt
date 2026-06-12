package com.uiery.keep.feature.lockhistory

import com.uiery.keep.R
import com.uiery.keep.model.LockHistoryModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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

    @Test
    fun selectedDateDisplayReportUsesOnlySessionsForThatDate() {
        val selectedDate = LocalDate.of(2026, 6, 6)
        val otherDate = selectedDate.minusDays(1)
        val groupedSessions = mapOf(
            selectedDate to listOf(
                lockHistory(id = 1, date = selectedDate, durationMinutes = 70, lockedApps = listOf("app.video", "app.social")),
                lockHistory(id = 2, date = selectedDate, durationMinutes = 65, lockedApps = listOf("app.video")),
            ),
            otherDate to listOf(
                lockHistory(id = 3, date = otherDate, durationMinutes = 1, lockedApps = listOf("app.other")),
            ),
        )

        val displayReport = buildLockHistoryDisplayReport(
            groupedSessions = groupedSessions,
            selectedDate = selectedDate,
            periodType = PeriodType.WEEK,
            fallbackReport = buildLockHistoryPerformanceReport(
                periodType = PeriodType.WEEK,
                totalDurationMillis = 136 * 60 * 1000L,
                sessionCount = 3,
                topApps = listOf("app.video" to 2, "app.social" to 1, "app.other" to 1),
            ),
        )

        assertEquals(listOf(selectedDate), displayReport.sessionsToShow.keys.toList())
        assertEquals(135 * 60 * 1000L, displayReport.totalDurationMillis)
        assertEquals(2, displayReport.sessionCount)
        assertEquals(listOf("app.video" to 2, "app.social" to 1), displayReport.topApps)
        assertEquals(LockHistoryPerformanceReportState.HAS_HISTORY, displayReport.performanceReport.state)
        assertEquals("2_3", displayReport.performanceReport.sessionCountBucket)
        assertEquals("120_239", displayReport.performanceReport.durationMinutesBucket)
        assertEquals("2_3", displayReport.performanceReport.topAppsCountBucket)
    }

    @Test
    fun selectedDateDisplayReportReturnsEmptyReportWhenDateHasNoSessions() {
        val selectedDate = LocalDate.of(2026, 6, 6)
        val groupedSessions = mapOf(
            selectedDate.minusDays(1) to listOf(
                lockHistory(
                    id = 1,
                    date = selectedDate.minusDays(1),
                    durationMinutes = 45,
                    lockedApps = listOf("app.video"),
                ),
            ),
        )

        val displayReport = buildLockHistoryDisplayReport(
            groupedSessions = groupedSessions,
            selectedDate = selectedDate,
            periodType = PeriodType.WEEK,
            fallbackReport = buildLockHistoryPerformanceReport(
                periodType = PeriodType.WEEK,
                totalDurationMillis = 45 * 60 * 1000L,
                sessionCount = 1,
                topApps = listOf("app.video" to 1),
            ),
        )

        assertEquals(emptyMap<LocalDate, List<LockHistoryModel>>(), displayReport.sessionsToShow)
        assertEquals(0L, displayReport.totalDurationMillis)
        assertEquals(0, displayReport.sessionCount)
        assertEquals(emptyList<Pair<String, Int>>(), displayReport.topApps)
        assertEquals(LockHistoryPerformanceReportState.EMPTY, displayReport.performanceReport.state)
        assertFalse(displayReport.performanceReport.shouldShowTopApps)
    }

    @Test
    fun selectedDateTopAppsCountsEachAppOncePerSession() {
        val selectedDate = LocalDate.of(2026, 6, 6)
        val groupedSessions = mapOf(
            selectedDate to listOf(
                lockHistory(
                    id = 1,
                    date = selectedDate,
                    durationMinutes = 30,
                    lockedApps = listOf("app.video", "app.video", "app.social"),
                ),
                lockHistory(
                    id = 2,
                    date = selectedDate,
                    durationMinutes = 30,
                    lockedApps = listOf("app.video"),
                ),
            ),
        )

        val displayReport = buildLockHistoryDisplayReport(
            groupedSessions = groupedSessions,
            selectedDate = selectedDate,
            periodType = PeriodType.WEEK,
            fallbackReport = buildLockHistoryPerformanceReport(
                periodType = PeriodType.WEEK,
                totalDurationMillis = 60 * 60 * 1000L,
                sessionCount = 2,
                topApps = listOf("app.video" to 2, "app.social" to 1),
            ),
        )

        assertEquals(listOf("app.video" to 2, "app.social" to 1), displayReport.topApps)
        assertEquals("2_3", displayReport.performanceReport.topAppsCountBucket)
    }

    @Test
    fun unselectedDisplayReportKeepsPeriodFallbackReport() {
        val report = buildLockHistoryPerformanceReport(
            periodType = PeriodType.MONTH,
            totalDurationMillis = 60 * 60 * 1000L,
            sessionCount = 2,
            topApps = listOf("app.video" to 2),
        )
        val groupedSessions = mapOf(
            LocalDate.of(2026, 6, 6) to listOf(
                lockHistory(id = 1, date = LocalDate.of(2026, 6, 6), durationMinutes = 60, lockedApps = listOf("app.video")),
                lockHistory(id = 2, date = LocalDate.of(2026, 6, 6), durationMinutes = 0, lockedApps = listOf("app.video")),
            ),
        )

        val displayReport = buildLockHistoryDisplayReport(
            groupedSessions = groupedSessions,
            selectedDate = null,
            periodType = PeriodType.MONTH,
            fallbackReport = report,
        )

        assertEquals(groupedSessions, displayReport.sessionsToShow)
        assertEquals(report, displayReport.performanceReport)
        assertEquals(60 * 60 * 1000L, displayReport.totalDurationMillis)
        assertEquals(2, displayReport.sessionCount)
        assertEquals(listOf("app.video" to 2), displayReport.topApps)
    }

    private fun lockHistory(
        id: Long,
        date: LocalDate,
        durationMinutes: Long,
        lockedApps: List<String>,
    ): LockHistoryModel {
        val start = LocalDateTime.of(date.year, date.month, date.dayOfMonth, 9, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return LockHistoryModel(
            id = id,
            startTimestamp = start,
            endTimestamp = start + durationMinutes * 60 * 1000L,
            durationMillis = durationMinutes * 60 * 1000L,
            lockedApps = lockedApps,
            isRoutine = false,
        )
    }
}

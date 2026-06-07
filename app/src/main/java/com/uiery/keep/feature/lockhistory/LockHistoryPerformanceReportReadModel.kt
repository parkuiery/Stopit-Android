package com.uiery.keep.feature.lockhistory

import androidx.annotation.StringRes
import com.uiery.keep.R
import com.uiery.keep.model.LockHistoryModel
import java.time.LocalDate

enum class LockHistoryPerformanceReportState {
    EMPTY,
    LOW_DATA,
    HAS_HISTORY,
}

data class LockHistoryPerformanceReportReadModel(
    val state: LockHistoryPerformanceReportState,
    val periodType: PeriodType,
    @StringRes val headlineResId: Int,
    @StringRes val supportingResId: Int,
    @StringRes val topAppsTitleResId: Int,
    @StringRes val topAppsSupportingResId: Int,
    val shouldShowTopApps: Boolean,
    val periodTypeAnalyticsValue: String,
    val sessionCountBucket: String,
    val durationMinutesBucket: String,
    val topAppsCountBucket: String,
)

data class LockHistoryDisplayReport(
    val sessionsToShow: Map<LocalDate, List<LockHistoryModel>>,
    val totalDurationMillis: Long,
    val sessionCount: Int,
    val topApps: List<Pair<String, Int>>,
    val performanceReport: LockHistoryPerformanceReportReadModel,
)

internal fun buildLockHistoryDisplayReport(
    groupedSessions: Map<LocalDate, List<LockHistoryModel>>,
    selectedDate: LocalDate?,
    periodType: PeriodType,
    fallbackReport: LockHistoryPerformanceReportReadModel,
): LockHistoryDisplayReport {
    val sessionsToShow = selectedDate?.let { date ->
        groupedSessions[date]?.let { sessions ->
            mapOf(date to sessions)
        } ?: emptyMap()
    } ?: groupedSessions
    val displaySessions = sessionsToShow.values.flatten()
    val displayTotalDuration = displaySessions.sumOf { it.durationMillis }
    val displaySessionCount = displaySessions.size
    val displayTopApps = displaySessions
        .flatMap { it.lockedApps }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(MAX_DISPLAY_TOP_APPS)
        .map { it.key to it.value }
    val displayPerformanceReport = if (selectedDate == null) {
        fallbackReport
    } else {
        buildLockHistoryPerformanceReport(
            periodType = periodType,
            totalDurationMillis = displayTotalDuration,
            sessionCount = displaySessionCount,
            topApps = displayTopApps,
        )
    }
    return LockHistoryDisplayReport(
        sessionsToShow = sessionsToShow,
        totalDurationMillis = displayTotalDuration,
        sessionCount = displaySessionCount,
        topApps = displayTopApps,
        performanceReport = displayPerformanceReport,
    )
}

internal fun buildLockHistoryPerformanceReport(
    periodType: PeriodType,
    totalDurationMillis: Long,
    sessionCount: Int,
    topApps: List<Pair<String, Int>>,
): LockHistoryPerformanceReportReadModel {
    val totalMinutes = (totalDurationMillis / 60_000L).coerceAtLeast(0L)
    val state = when {
        sessionCount == 0 || totalMinutes == 0L -> LockHistoryPerformanceReportState.EMPTY
        sessionCount == 1 || totalMinutes < LOW_DATA_DURATION_MINUTES -> LockHistoryPerformanceReportState.LOW_DATA
        else -> LockHistoryPerformanceReportState.HAS_HISTORY
    }
    val headlineResId = when (state) {
        LockHistoryPerformanceReportState.EMPTY -> when (periodType) {
            PeriodType.WEEK -> R.string.lock_history_performance_empty_week_headline
            PeriodType.MONTH -> R.string.lock_history_performance_empty_month_headline
        }
        LockHistoryPerformanceReportState.LOW_DATA -> R.string.lock_history_performance_low_data_headline
        LockHistoryPerformanceReportState.HAS_HISTORY -> when (periodType) {
            PeriodType.WEEK -> R.string.lock_history_performance_week_headline
            PeriodType.MONTH -> R.string.lock_history_performance_month_headline
        }
    }
    val supportingResId = when (state) {
        LockHistoryPerformanceReportState.EMPTY -> R.string.lock_history_performance_empty_supporting
        LockHistoryPerformanceReportState.LOW_DATA -> R.string.lock_history_performance_low_data_supporting
        LockHistoryPerformanceReportState.HAS_HISTORY -> R.string.lock_history_performance_sessions_supporting
    }
    val shouldShowTopApps = topApps.isNotEmpty()
    return LockHistoryPerformanceReportReadModel(
        state = state,
        periodType = periodType,
        headlineResId = headlineResId,
        supportingResId = supportingResId,
        topAppsTitleResId = R.string.lock_history_top_apps_title,
        topAppsSupportingResId = if (shouldShowTopApps) {
            R.string.lock_history_top_apps_positive_supporting
        } else {
            R.string.lock_history_top_apps_empty_supporting
        },
        shouldShowTopApps = shouldShowTopApps,
        periodTypeAnalyticsValue = when (periodType) {
            PeriodType.WEEK -> "week"
            PeriodType.MONTH -> "month"
        },
        sessionCountBucket = bucketSessionCount(sessionCount),
        durationMinutesBucket = bucketDurationMinutes(totalMinutes),
        topAppsCountBucket = bucketTopAppsCount(topApps.size),
    )
}

private fun bucketSessionCount(sessionCount: Int): String = when {
    sessionCount <= 0 -> "0"
    sessionCount == 1 -> "1"
    sessionCount <= 3 -> "2_3"
    sessionCount <= 6 -> "4_6"
    else -> "7_plus"
}

private fun bucketDurationMinutes(minutes: Long): String = when {
    minutes <= 0L -> "0"
    minutes <= 29L -> "1_29"
    minutes <= 59L -> "30_59"
    minutes <= 119L -> "60_119"
    minutes <= 239L -> "120_239"
    else -> "240_plus"
}

private fun bucketTopAppsCount(count: Int): String = when {
    count <= 0 -> "0"
    count == 1 -> "1"
    count <= 3 -> "2_3"
    else -> "4_plus"
}

private const val LOW_DATA_DURATION_MINUTES = 5L
private const val MAX_DISPLAY_TOP_APPS = 3

package com.uiery.keep.feature.lockhistory

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.model.LockHistoryModel
import com.uiery.keep.service.summarizeLockHistoryLedger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class PeriodType {
    WEEK, MONTH
}

@HiltViewModel
class LockHistoryViewModel @Inject constructor(
    private val lockHistoryRepository: LockHistoryRepository,
    private val analytics: KeepAnalytics,
) : ContainerHost<LockHistoryUiState, LockHistorySideEffect>, ViewModel() {

    override val container: Container<LockHistoryUiState, LockHistorySideEffect> =
        container(LockHistoryUiState())

    init {
        analytics.logScreenView(KeepAnalyticsScreen.LOCK_HISTORY)
        loadHistory()
    }

    internal fun selectPeriodType(periodType: PeriodType) = intent {
        reduce {
            state.copy(
                periodType = periodType,
                currentDate = LocalDate.now(),
                selectedDate = null,
                focusSummarySharePayload = null,
            )
        }
        loadHistory()
    }

    internal fun moveToPreviousPeriod() = intent {
        val newDate = when (state.periodType) {
            PeriodType.WEEK -> state.currentDate.minusWeeks(1)
            PeriodType.MONTH -> state.currentDate.minusMonths(1)
        }
        reduce { state.copy(currentDate = newDate) }
        loadHistory()
    }

    internal fun moveToNextPeriod() = intent {
        val newDate = when (state.periodType) {
            PeriodType.WEEK -> state.currentDate.plusWeeks(1)
            PeriodType.MONTH -> state.currentDate.plusMonths(1)
        }
        reduce { state.copy(currentDate = newDate) }
        loadHistory()
    }

    internal fun selectDate(date: LocalDate) = intent {
        val newSelectedDate = if (state.selectedDate == date) null else date
        reduce { state.copy(selectedDate = newSelectedDate) }
    }

    private fun loadHistory() = intent {
        val (startDate, endDate) = getDateRange(state.periodType, state.currentDate)
        val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val sessions = lockHistoryRepository.sessionsInRange(startMillis, endMillis)
            .firstOrNull()
            ?: emptyList()

        val summary = summarizeLockHistoryLedger(sessions)
        val performanceReport = buildLockHistoryPerformanceReport(
            periodType = state.periodType,
            totalDurationMillis = summary.totalDurationMillis,
            sessionCount = summary.sessionCount,
            topApps = summary.topApps.map { it to 1 },
        )

        reduce {
            state.copy(
                startDate = startDate,
                endDate = endDate,
                groupedSessions = summary.groupedSessions,
                totalDuration = summary.totalDurationMillis,
                sessionCount = summary.sessionCount,
                topApps = summary.topApps,
                durationByDate = summary.durationByDate,
                selectedDate = null,
                performanceReport = performanceReport,
                focusSummarySharePayload = buildFocusSummarySharePayload(
                    periodType = state.periodType,
                    sessionCount = summary.sessionCount,
                    totalDurationMillis = summary.totalDurationMillis,
                ),
            )
        }
        trackPerformanceReportViewed(performanceReport)
    }

    private fun trackPerformanceReportViewed(performanceReport: LockHistoryPerformanceReportReadModel) {
        analytics.trackLockHistoryPerformanceSummaryViewed(
            periodType = performanceReport.periodTypeAnalyticsValue,
            reportState = performanceReport.state.analyticsValue,
            sessionCountBucket = performanceReport.sessionCountBucket,
            durationMinutesBucket = performanceReport.durationMinutesBucket,
        )
        if (performanceReport.shouldShowTopApps) {
            analytics.trackLockHistoryTopAppsViewed(
                periodType = performanceReport.periodTypeAnalyticsValue,
                topAppsCountBucket = performanceReport.topAppsCountBucket,
            )
        }
    }

    internal fun shareFocusSummary() = intent {
        val payload = state.focusSummarySharePayload ?: return@intent
        analytics.trackFocusSummaryShareTapped(
            periodType = payload.periodType,
            sessionCountBucket = payload.sessionCountBucket,
            durationMinutesBucket = payload.durationMinutesBucket,
        )
        postSideEffect(LockHistorySideEffect.ShareFocusSummary(payload))
    }

    internal fun onFocusSummaryShareSheetOpened(payload: FocusSummarySharePayload) {
        analytics.trackFocusSummaryShareSheetOpened(
            periodType = payload.periodType,
            sessionCountBucket = payload.sessionCountBucket,
            durationMinutesBucket = payload.durationMinutesBucket,
        )
    }

    internal fun onFocusSummaryShareFailed(payload: FocusSummarySharePayload) {
        analytics.trackFocusSummaryShareFailed(
            periodType = payload.periodType,
            reason = "activity_not_found",
        )
    }

    private fun getDateRange(periodType: PeriodType, currentDate: LocalDate): Pair<LocalDate, LocalDate> {
        return when (periodType) {
            PeriodType.WEEK -> {
                val startOfWeek = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val endOfWeek = currentDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                startOfWeek to endOfWeek
            }
            PeriodType.MONTH -> {
                val startOfMonth = currentDate.withDayOfMonth(1)
                val endOfMonth = currentDate.with(TemporalAdjusters.lastDayOfMonth())
                startOfMonth to endOfMonth
            }
        }
    }
}

data class LockHistoryUiState(
    val periodType: PeriodType = PeriodType.WEEK,
    val currentDate: LocalDate = LocalDate.now(),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val groupedSessions: Map<LocalDate, List<LockHistoryModel>> = emptyMap(),
    val totalDuration: Long = 0L,
    val sessionCount: Int = 0,
    val topApps: List<String> = emptyList(),
    val durationByDate: Map<LocalDate, Long> = emptyMap(),
    val selectedDate: LocalDate? = null,
    val performanceReport: LockHistoryPerformanceReportReadModel = buildLockHistoryPerformanceReport(
        periodType = PeriodType.WEEK,
        totalDurationMillis = 0L,
        sessionCount = 0,
        topApps = emptyList(),
    ),
    val focusSummarySharePayload: FocusSummarySharePayload? = null,
)

sealed class LockHistorySideEffect {
    data class ShareFocusSummary(val payload: FocusSummarySharePayload) : LockHistorySideEffect()
}

private val LockHistoryPerformanceReportState.analyticsValue: String
    get() = when (this) {
        LockHistoryPerformanceReportState.EMPTY -> "empty"
        LockHistoryPerformanceReportState.LOW_DATA -> "low_data"
        LockHistoryPerformanceReportState.HAS_HISTORY -> "has_history"
    }

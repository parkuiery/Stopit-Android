package com.uiery.keep.feature.lockhistory

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestionStore
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.model.RoutineModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class LockHistoryViewModelShareTest {
    @Test
    fun weeklyHistoryBuildsSharePayloadAndTracksTappedEventWithBuckets() = runBlocking {
        val analytics = RecordingLockHistoryAnalytics()
        val viewModel = createViewModel(
            lockHistoryRepository = LockHistoryRepository(
                LockHistoryDaoWithSessions(
                    listOf(
                        sessionInCurrentWeek(
                            durationMillis = 130 * 60 * 1000L,
                        ),
                        sessionInCurrentWeek(
                            durationMillis = 10 * 60 * 1000L,
                        ),
                    ),
                ),
            ),
            analytics = analytics,
            focusSummaryShareTextProvider = FakeFocusSummaryShareTextProvider(),
        )

        assertEquals(listOf(KeepAnalyticsScreen.LOCK_HISTORY), analytics.screenViews)

        waitForHistoryLoad(viewModel)
        waitForAnalyticsEventCount(analytics, 2)
        val payload = viewModel.container.stateFlow.value.focusSummarySharePayload

        assertNotNull(payload)
        requireNotNull(payload)
        assertEquals("week", payload.periodType)
        assertEquals("2_3", payload.sessionCountBucket)
        assertEquals("120_239", payload.durationMinutesBucket)

        viewModel.shareFocusSummary()
        waitForAnalyticsEventCount(analytics, 3)

        assertEquals(
            listOf(
                ShareAnalyticsEvent(
                    name = "performance_summary_viewed",
                    periodType = "week",
                    reportState = "has_history",
                    sessionCountBucket = "2_3",
                    durationMinutesBucket = "120_239",
                    topAppsCountBucket = null,
                    reason = null,
                ),
                ShareAnalyticsEvent(
                    name = "top_apps_viewed",
                    periodType = "week",
                    reportState = null,
                    sessionCountBucket = null,
                    durationMinutesBucket = null,
                    topAppsCountBucket = "1",
                    reason = null,
                ),
                ShareAnalyticsEvent(
                    name = "tapped",
                    periodType = "week",
                    reportState = null,
                    sessionCountBucket = "2_3",
                    durationMinutesBucket = "120_239",
                    topAppsCountBucket = null,
                    reason = null,
                ),
            ),
            analytics.events,
        )
    }

    @Test
    fun monthlyHistoryDoesNotExposeSharePayload() = runBlocking {
        val analytics = RecordingLockHistoryAnalytics()
        val viewModel = createViewModel(
            lockHistoryRepository = LockHistoryRepository(
                MonthFetchDelayingLockHistoryDao(
                    listOf(sessionInCurrentWeek(durationMillis = 30 * 60 * 1000L)),
                ),
            ),
            analytics = analytics,
            focusSummaryShareTextProvider = FakeFocusSummaryShareTextProvider(),
        )

        waitForHistoryLoad(viewModel)
        assertNotNull(viewModel.container.stateFlow.value.focusSummarySharePayload)

        viewModel.selectPeriodType(PeriodType.MONTH)
        waitUntil { viewModel.container.stateFlow.value.periodType == PeriodType.MONTH }

        assertNull(viewModel.container.stateFlow.value.focusSummarySharePayload)
    }

    @Test
    fun emptyHistoryTracksOnlySummaryPerformanceEventWithoutTopApps() = runBlocking {
        val analytics = RecordingLockHistoryAnalytics()
        val viewModel = createViewModel(
            lockHistoryRepository = LockHistoryRepository(
                LockHistoryDaoWithSessions(emptyList()),
            ),
            analytics = analytics,
            focusSummaryShareTextProvider = FakeFocusSummaryShareTextProvider(),
        )

        waitForAnalyticsEventCount(analytics, 1)

        assertEquals(
            listOf(
                ShareAnalyticsEvent(
                    name = "performance_summary_viewed",
                    periodType = "week",
                    reportState = "empty",
                    sessionCountBucket = "0",
                    durationMinutesBucket = "0",
                    topAppsCountBucket = null,
                    reason = null,
                ),
            ),
            analytics.events,
        )
        assertEquals(LockHistoryPerformanceReportState.EMPTY, viewModel.container.stateFlow.value.performanceReport.state)
    }

    @Test
    fun selectingDateTracksSelectedDatePerformanceSummaryAndTopApps() = runBlocking {
        val analytics = RecordingLockHistoryAnalytics()
        val selectedDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val viewModel = createViewModel(
            lockHistoryRepository = LockHistoryRepository(
                LockHistoryDaoWithSessions(
                    listOf(
                        sessionInCurrentWeek(durationMillis = 45 * 60 * 1000L),
                        sessionInCurrentWeek(durationMillis = 10 * 60 * 1000L),
                    ),
                ),
            ),
            analytics = analytics,
            focusSummaryShareTextProvider = FakeFocusSummaryShareTextProvider(),
        )

        waitForHistoryLoad(viewModel)
        waitForAnalyticsEventCount(analytics, 2)

        viewModel.selectDate(selectedDate)
        waitForAnalyticsEventCount(analytics, 4)

        assertEquals(
            listOf(
                ShareAnalyticsEvent(
                    name = "performance_summary_viewed",
                    periodType = "selected_date",
                    reportState = "has_history",
                    sessionCountBucket = "2_3",
                    durationMinutesBucket = "30_59",
                    topAppsCountBucket = null,
                    reason = null,
                ),
                ShareAnalyticsEvent(
                    name = "top_apps_viewed",
                    periodType = "selected_date",
                    reportState = null,
                    sessionCountBucket = null,
                    durationMinutesBucket = null,
                    topAppsCountBucket = "1",
                    reason = null,
                ),
            ),
            analytics.events.takeLast(2),
        )
    }

    private fun createViewModel(
        lockHistoryRepository: LockHistoryRepository,
        analytics: RecordingLockHistoryAnalytics,
        focusSummaryShareTextProvider: FocusSummaryShareTextProvider = FakeFocusSummaryShareTextProvider(),
    ): LockHistoryViewModel = LockHistoryViewModel(
        lockHistoryRepository = lockHistoryRepository,
        routineRepository = EmptyLockHistoryRoutineRepository(),
        repeatBlockSuggestionStore = RepeatBlockRoutineSuggestionStore(FakeDataStore(mutablePreferencesOf())),
        analytics = analytics,
        focusSummaryShareTextProvider = focusSummaryShareTextProvider,
    )

    private suspend fun waitForHistoryLoad(viewModel: LockHistoryViewModel) {
        waitUntil { viewModel.container.stateFlow.value.sessionCount > 0 }
    }

    private suspend fun waitForAnalyticsEventCount(
        analytics: RecordingLockHistoryAnalytics,
        expectedCount: Int,
    ) {
        waitUntil { analytics.events.size >= expectedCount }
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(50) {
            if (predicate()) return
            delay(10)
        }
    }

    private fun sessionInCurrentWeek(durationMillis: Long): LockHistoryEntity {
        val startOfWeek = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return LockHistoryEntity(
            startTimestamp = startOfWeek + 60_000L,
            endTimestamp = startOfWeek + 60_000L + durationMillis,
            durationMillis = durationMillis,
            lockedApps = listOf("com.example.sensitive"),
            isRoutine = false,
        )
    }
}

private class FakeFocusSummaryShareTextProvider : FocusSummaryShareTextProvider {
    override fun buildText(request: FocusSummaryShareTextRequest): String =
        "Focus summary ${request.sessionCount} sessions / ${request.durationMinutes} minutes\n${request.playStoreUrl}"
}

private open class LockHistoryDaoWithSessions(
    private val sessions: List<LockHistoryEntity>,
) : LockHistoryDao {
    override suspend fun insert(entity: LockHistoryEntity) = Unit

    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> =
        flowOf(sessions.filter { it.startTimestamp >= startMillis && it.startTimestamp < endMillis })

    override fun fetchAll(): Flow<List<LockHistoryEntity>> = flowOf(sessions)

    override suspend fun countSuccessfulSessions(): Int = sessions.size

    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int =
        sessions.count { it.startTimestamp >= timestampMillis }
}

private class EmptyLockHistoryRoutineRepository : RoutineRepository {
    override fun fetchAll(): Flow<List<RoutineModel>> = flowOf(emptyList())
}

private class MonthFetchDelayingLockHistoryDao(
    sessions: List<LockHistoryEntity>,
) : LockHistoryDaoWithSessions(sessions) {
    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> {
        val startDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMillis - 1).atZone(ZoneId.systemDefault()).toLocalDate()
        if (startDate.dayOfMonth == 1 && endDate.dayOfMonth == endDate.lengthOfMonth()) {
            return flow {
                delay(500)
                emit(super.fetchByDateRange(startMillis, endMillis).firstOrNull() ?: emptyList())
            }
        }
        return super.fetchByDateRange(startMillis, endMillis)
    }
}

private data class ShareAnalyticsEvent(
    val name: String,
    val periodType: String,
    val reportState: String?,
    val sessionCountBucket: String?,
    val durationMinutesBucket: String?,
    val topAppsCountBucket: String?,
    val reason: String?,
)

private class RecordingLockHistoryAnalytics : KeepAnalytics {
    val events = mutableListOf<ShareAnalyticsEvent>()
    val screenViews = mutableListOf<String>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit

    override fun logScreenView(screenName: String) {
        screenViews += screenName
    }

    override fun setUserProperty(name: String, value: String) = Unit

    override fun trackFirstOpen() = Unit

    override fun trackOnboardingStepView(stepName: String) = Unit

    override fun trackOnboardingStepComplete(stepName: String) = Unit

    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit

    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit

    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit

    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit

    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit

    override fun trackFocusSummaryShareTapped(
        periodType: String,
        sessionCountBucket: String,
        durationMinutesBucket: String,
    ) {
        events += ShareAnalyticsEvent(
            name = "tapped",
            periodType = periodType,
            reportState = null,
            sessionCountBucket = sessionCountBucket,
            durationMinutesBucket = durationMinutesBucket,
            topAppsCountBucket = null,
            reason = null,
        )
    }

    override fun trackFocusSummaryShareSheetOpened(
        periodType: String,
        sessionCountBucket: String,
        durationMinutesBucket: String,
    ) {
        events += ShareAnalyticsEvent(
            name = "sheet_opened",
            periodType = periodType,
            reportState = null,
            sessionCountBucket = sessionCountBucket,
            durationMinutesBucket = durationMinutesBucket,
            topAppsCountBucket = null,
            reason = null,
        )
    }

    override fun trackFocusSummaryShareFailed(periodType: String, reason: String) {
        events += ShareAnalyticsEvent(
            name = "failed",
            periodType = periodType,
            reportState = null,
            sessionCountBucket = null,
            durationMinutesBucket = null,
            topAppsCountBucket = null,
            reason = reason,
        )
    }

    override fun trackLockHistoryPerformanceSummaryViewed(
        periodType: String,
        reportState: String,
        sessionCountBucket: String,
        durationMinutesBucket: String,
    ) {
        events += ShareAnalyticsEvent(
            name = "performance_summary_viewed",
            periodType = periodType,
            reportState = reportState,
            sessionCountBucket = sessionCountBucket,
            durationMinutesBucket = durationMinutesBucket,
            topAppsCountBucket = null,
            reason = null,
        )
    }

    override fun trackLockHistoryTopAppsViewed(
        periodType: String,
        topAppsCountBucket: String,
    ) {
        events += ShareAnalyticsEvent(
            name = "top_apps_viewed",
            periodType = periodType,
            reportState = null,
            sessionCountBucket = null,
            durationMinutesBucket = null,
            topAppsCountBucket = topAppsCountBucket,
            reason = null,
        )
    }
}

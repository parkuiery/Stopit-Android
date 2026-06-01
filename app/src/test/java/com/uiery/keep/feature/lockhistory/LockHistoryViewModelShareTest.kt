package com.uiery.keep.feature.lockhistory

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
        val viewModel = LockHistoryViewModel(
            lockHistoryDao = LockHistoryDaoWithSessions(
                listOf(
                    sessionInCurrentWeek(
                        durationMillis = 130 * 60 * 1000L,
                    ),
                    sessionInCurrentWeek(
                        durationMillis = 10 * 60 * 1000L,
                    ),
                ),
            ),
            analytics = analytics,
        )

        assertEquals(listOf(KeepAnalyticsScreen.LOCK_HISTORY), analytics.screenViews)

        waitForHistoryLoad(viewModel)
        val payload = viewModel.container.stateFlow.value.focusSummarySharePayload

        assertNotNull(payload)
        requireNotNull(payload)
        assertEquals("week", payload.periodType)
        assertEquals("2_3", payload.sessionCountBucket)
        assertEquals("120_239", payload.durationMinutesBucket)

        viewModel.shareFocusSummary()
        waitForAnalyticsEvent(analytics)

        assertEquals(
            listOf(
                ShareAnalyticsEvent(
                    name = "tapped",
                    periodType = "week",
                    sessionCountBucket = "2_3",
                    durationMinutesBucket = "120_239",
                    reason = null,
                ),
            ),
            analytics.events,
        )
    }

    @Test
    fun monthlyHistoryDoesNotExposeSharePayload() = runBlocking {
        val analytics = RecordingLockHistoryAnalytics()
        val viewModel = LockHistoryViewModel(
            lockHistoryDao = LockHistoryDaoWithSessions(
                listOf(sessionInCurrentWeek(durationMillis = 30 * 60 * 1000L)),
            ),
            analytics = analytics,
        )

        waitForHistoryLoad(viewModel)
        viewModel.selectPeriodType(PeriodType.MONTH)
        waitUntil { viewModel.container.stateFlow.value.periodType == PeriodType.MONTH }

        assertNull(viewModel.container.stateFlow.value.focusSummarySharePayload)
    }

    private suspend fun waitForHistoryLoad(viewModel: LockHistoryViewModel) {
        waitUntil { viewModel.container.stateFlow.value.sessionCount > 0 }
    }

    private suspend fun waitForAnalyticsEvent(analytics: RecordingLockHistoryAnalytics) {
        waitUntil { analytics.events.isNotEmpty() }
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

private class LockHistoryDaoWithSessions(
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

private data class ShareAnalyticsEvent(
    val name: String,
    val periodType: String,
    val sessionCountBucket: String?,
    val durationMinutesBucket: String?,
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
            sessionCountBucket = sessionCountBucket,
            durationMinutesBucket = durationMinutesBucket,
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
            sessionCountBucket = sessionCountBucket,
            durationMinutesBucket = durationMinutesBucket,
            reason = null,
        )
    }

    override fun trackFocusSummaryShareFailed(periodType: String, reason: String) {
        events += ShareAnalyticsEvent(
            name = "failed",
            periodType = periodType,
            sessionCountBucket = null,
            durationMinutesBucket = null,
            reason = reason,
        )
    }
}

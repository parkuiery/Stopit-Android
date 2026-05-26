package com.uiery.keep.feature.history

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryViewModelTest {
    @Test
    fun initLogsHistoryScreenView() = runBlocking {
        val analytics = HistoryRecordingKeepAnalytics()

        HistoryViewModel(
            dataStore = FakeDataStore(),
            analytics = analytics,
            lockHistoryDao = HistoryLockHistoryDao(),
        )

        assertEquals(listOf(KeepAnalyticsScreen.HISTORY), analytics.screenViews)
    }

    @Test
    fun roomLedgerSummaryWinsWhenLegacyPreferenceCacheDrifts() = runBlocking {
        val analytics = HistoryRecordingKeepAnalytics()
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.LONG_BLOCK_TIME] = 99_999L
                this[PreferencesKey.TOTAL_BLOCK_TIME] = 111_111L
            }
        val viewModel =
            HistoryViewModel(
                dataStore = dataStore,
                analytics = analytics,
                lockHistoryDao =
                    HistoryLockHistoryDao(
                        sessions = listOf(
                            historyEntity(start = 1_000L, end = 6_000L),
                            historyEntity(start = 7_000L, end = 19_000L),
                        ),
                    ),
            )

        waitForHistoryLoad(viewModel)

        assertEquals(17_000L, viewModel.container.stateFlow.value.totalBlockTime)
        assertEquals(12_000L, viewModel.container.stateFlow.value.longBlockTime)
    }

    @Test
    fun fallsBackToLegacyPreferenceCacheWhenLedgerIsEmpty() = runBlocking {
        val analytics = HistoryRecordingKeepAnalytics()
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.LONG_BLOCK_TIME] = 7_000L
                this[PreferencesKey.TOTAL_BLOCK_TIME] = 21_000L
            }
        val viewModel =
            HistoryViewModel(
                dataStore = dataStore,
                analytics = analytics,
                lockHistoryDao = HistoryLockHistoryDao(),
            )

        waitForHistoryLoad(viewModel)

        assertEquals(21_000L, viewModel.container.stateFlow.value.totalBlockTime)
        assertEquals(7_000L, viewModel.container.stateFlow.value.longBlockTime)
    }

    private suspend fun waitForHistoryLoad(viewModel: HistoryViewModel) {
        repeat(20) {
            val state = viewModel.container.stateFlow.value
            if (state.totalBlockTime != 0L || state.longBlockTime != 0L) {
                return
            }
            delay(10)
        }
    }

    private fun historyEntity(start: Long, end: Long) =
        LockHistoryEntity(
            startTimestamp = start,
            endTimestamp = end,
            durationMillis = end - start,
            lockedApps = listOf("com.example.app"),
            isRoutine = false,
        )
}

private class HistoryLockHistoryDao(
    private val sessions: List<LockHistoryEntity> = emptyList(),
) : LockHistoryDao {
    override suspend fun insert(entity: LockHistoryEntity) = Unit

    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> = flowOf(emptyList())

    override fun fetchAll(): Flow<List<LockHistoryEntity>> = flowOf(sessions)

    override suspend fun countSuccessfulSessions(): Int = sessions.size

    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int =
        sessions.count { it.startTimestamp >= timestampMillis }
}

private class HistoryRecordingKeepAnalytics : KeepAnalytics {
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
}

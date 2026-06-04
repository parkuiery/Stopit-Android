package com.uiery.keep.service

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUnlockCoordinatorTest {
    @Test
    fun availabilitySanitizesSettingsAndCalculatesRemainingUnlocks() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.EMERGENCY_UNLOCK_ENABLED] = true
                this[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = 6
                this[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS] = setOf("15", "3", "bad")
                this[PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED] = false
            }
        val dao = RecordingEmergencyUnlockDao(todayCount = 2)
        val analytics = RecordingEmergencyUnlockAnalytics()
        val coordinator = EmergencyUnlockCoordinator(
            settingsStore = EmergencyUnlockSettingsStore(dataStore),
            blockingStateStore = BlockingStateStore(dataStore),
            emergencyUnlockDao = dao,
            analytics = analytics,
        )

        val availability = coordinator.readAvailability()

        assertTrue(availability.enabled)
        assertEquals(3, availability.dailyLimit)
        assertEquals(listOf(3, 15), availability.durationOptions)
        assertFalse(availability.reasonRequired)
        assertFalse(availability.dailyLimitReached)
        assertEquals(1, availability.dailyUnlockRemaining)
        assertEquals(1, dao.countTodayCalls.size)
        assertTrue(dao.countSinceCalls.isEmpty())
    }

    @Test
    fun manualResetModeUsesCountSinceManualResetTimestampForAvailability() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.EMERGENCY_UNLOCK_AUTO_RESET_ENABLED] = false
                this[PreferencesKey.EMERGENCY_UNLOCK_MANUAL_RESET_AT] = 10_000L
                this[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = 3
            }
        val dao = RecordingEmergencyUnlockDao(todayCount = 0, sinceCount = 2)
        val coordinator = createCoordinator(dataStore = dataStore, dao = dao)

        val availability = coordinator.readAvailability()

        assertEquals(EmergencyUnlockAvailabilityReason.Available, availability.reason)
        assertEquals(1, availability.dailyUnlockRemaining)
        assertTrue(dao.countTodayCalls.isEmpty())
        assertEquals(listOf(10_000L), dao.countSinceCalls)
    }

    @Test
    fun manualResetModeRejectsUnlockWhenCountSinceManualResetExhaustsLimit() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.EMERGENCY_UNLOCK_AUTO_RESET_ENABLED] = false
                this[PreferencesKey.EMERGENCY_UNLOCK_MANUAL_RESET_AT] = 20_000L
                this[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = 3
                this[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS] = setOf("3")
                this[PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED] = false
            }
        val dao = RecordingEmergencyUnlockDao(todayCount = 0, sinceCount = 3)
        val coordinator = createCoordinator(dataStore = dataStore, dao = dao)

        val result = coordinator.completeUnlock(
            source = AnalyticsSource.LOCK_SCREEN,
            reason = EMERGENCY_UNLOCK_REASON_NOT_REQUIRED,
            customReason = null,
            apps = setOf("com.example.app"),
            durationMinutes = 3,
            nowMillis = 30_000L,
        )

        assertTrue(result is EmergencyUnlockRequestResult.Rejected)
        val rejected = result as EmergencyUnlockRequestResult.Rejected
        assertEquals(EmergencyUnlockAvailabilityReason.DailyLimitExhausted, rejected.availability.reason)
        assertTrue(dao.inserted.isEmpty())
        assertTrue(dao.countTodayCalls.isEmpty())
        assertEquals(listOf(20_000L), dao.countSinceCalls)
    }

    @Test
    fun manualResetUpdatesTimestampWithoutDeletingUnlockHistory() = runBlocking {
        val dataStore = FakeDataStore()
        val dao = RecordingEmergencyUnlockDao(todayCount = 0)
        val coordinator = createCoordinator(dataStore = dataStore, dao = dao)

        coordinator.markManualReset(nowMillis = 40_000L)

        assertEquals(40_000L, dataStore.snapshot()[PreferencesKey.EMERGENCY_UNLOCK_MANUAL_RESET_AT])
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun completeUnlockPersistsStateAndAnalyticsForAnyEntryPoint() = runBlocking {
        val blockRun = completeUnlock(source = AnalyticsSource.BLOCK_SCREEN)
        val lockRun = completeUnlock(source = AnalyticsSource.LOCK_SCREEN)

        assertEquals(blockRun.completed, lockRun.completed.copy(source = blockRun.completed.source))
        assertEquals(EmergencyUnlockState.current, lockRun.completed.stateSnapshot)
        assertEquals(setOf("com.example.app"), lockRun.completed.stateSnapshot.unlockedApps)
        assertEquals(lockRun.completed.expireTimeMillis, lockRun.completed.stateSnapshot.expireTimeMillis)
        assertEquals(setOf("com.example.app"), lockRun.persistedApps)
        assertEquals(lockRun.completed.expireTimeMillis, lockRun.persistedExpireTime)
        assertEquals(
            listOf(
                AnalyticsRecord.Used(AnalyticsSource.LOCK_SCREEN, 2),
                AnalyticsRecord.Completed(reason = "work", durationMinutes = 5, remainingUnlocks = 2),
            ),
            lockRun.analytics.records,
        )
    }

    @Test
    fun disabledSettingIsNotReportedAsDailyLimitReached() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.EMERGENCY_UNLOCK_ENABLED] = false
                this[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = 3
            }
        val coordinator = createCoordinator(dataStore = dataStore, todayCount = 0)

        val availability = coordinator.readAvailability()

        assertFalse(availability.enabled)
        assertEquals(EmergencyUnlockAvailabilityReason.Disabled, availability.reason)
        assertFalse(availability.dailyLimitReached)
        assertEquals(3, availability.dailyUnlockRemaining)
    }

    @Test
    fun zeroDailyLimitIsSeparateFromExhaustedDailyLimit() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.EMERGENCY_UNLOCK_ENABLED] = true
                this[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = 0
            }
        val coordinator = createCoordinator(dataStore = dataStore, todayCount = 0)

        val availability = coordinator.readAvailability()

        assertEquals(EmergencyUnlockAvailabilityReason.DailyLimitZero, availability.reason)
        assertFalse(availability.dailyLimitReached)
        assertEquals(0, availability.dailyUnlockRemaining)
    }

    @Test
    fun exhaustedDailyLimitKeepsDailyLimitReachedReason() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.EMERGENCY_UNLOCK_ENABLED] = true
                this[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = 3
            }
        val coordinator = createCoordinator(dataStore = dataStore, todayCount = 3)

        val availability = coordinator.readAvailability()

        assertEquals(EmergencyUnlockAvailabilityReason.DailyLimitExhausted, availability.reason)
        assertTrue(availability.dailyLimitReached)
        assertEquals(0, availability.dailyUnlockRemaining)
    }

    @Test
    fun invalidRequestReturnsRefreshedAvailabilityWithoutPersistence() = runBlocking {
        val dataStore = FakeDataStore()
        val dao = RecordingEmergencyUnlockDao(todayCount = 3)
        val analytics = RecordingEmergencyUnlockAnalytics()
        val coordinator = EmergencyUnlockCoordinator(
            settingsStore = EmergencyUnlockSettingsStore(dataStore),
            blockingStateStore = BlockingStateStore(dataStore),
            emergencyUnlockDao = dao,
            analytics = analytics,
        )

        val result =
            coordinator.completeUnlock(
                source = AnalyticsSource.BLOCK_SCREEN,
                reason = "work",
                customReason = null,
                apps = setOf("com.example.app"),
                durationMinutes = 3,
                nowMillis = 1_000L,
            )

        assertTrue(result is EmergencyUnlockRequestResult.Rejected)
        val rejected = result as EmergencyUnlockRequestResult.Rejected
        assertEquals(EmergencyUnlockAvailabilityReason.DailyLimitExhausted, rejected.availability.reason)
        assertTrue(rejected.availability.dailyLimitReached)
        assertEquals(0, rejected.availability.dailyUnlockRemaining)
        assertTrue(dao.inserted.isEmpty())
        assertTrue(analytics.records.isEmpty())
        val snapshot = dataStore.snapshot()
        assertNull(snapshot[PreferencesKey.EMERGENCY_UNLOCK_APPS])
        assertNull(snapshot[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME])
    }

    private fun createCoordinator(
        dataStore: FakeDataStore,
        todayCount: Int,
        analytics: RecordingEmergencyUnlockAnalytics = RecordingEmergencyUnlockAnalytics(),
    ): EmergencyUnlockCoordinator =
        createCoordinator(
            dataStore = dataStore,
            dao = RecordingEmergencyUnlockDao(todayCount = todayCount),
            analytics = analytics,
        )

    private fun createCoordinator(
        dataStore: FakeDataStore,
        dao: RecordingEmergencyUnlockDao,
        analytics: RecordingEmergencyUnlockAnalytics = RecordingEmergencyUnlockAnalytics(),
    ): EmergencyUnlockCoordinator =
        EmergencyUnlockCoordinator(
            settingsStore = EmergencyUnlockSettingsStore(dataStore),
            blockingStateStore = BlockingStateStore(dataStore),
            emergencyUnlockDao = dao,
            analytics = analytics,
        )

    private suspend fun completeUnlock(source: String): CompletedUnlockFixture {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.EMERGENCY_UNLOCK_ENABLED] = true
                this[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = 3
                this[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS] = setOf("3", "5")
                this[PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED] = true
            }
        val dao = RecordingEmergencyUnlockDao(todayCount = 0)
        val analytics = RecordingEmergencyUnlockAnalytics()
        val coordinator = EmergencyUnlockCoordinator(
            settingsStore = EmergencyUnlockSettingsStore(dataStore),
            blockingStateStore = BlockingStateStore(dataStore),
            emergencyUnlockDao = dao,
            analytics = analytics,
        )
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY

        val result =
            coordinator.completeUnlock(
                source = source,
                reason = "work",
                customReason = "email",
                apps = setOf("com.example.app"),
                durationMinutes = 5,
                nowMillis = 1_000L,
            )

        check(result is EmergencyUnlockRequestResult.Completed)
        val snapshot = dataStore.snapshot()
        return CompletedUnlockFixture(
            completed = result,
            analytics = analytics,
            stateSnapshot = EmergencyUnlockState.current,
            persistedApps = snapshot[PreferencesKey.EMERGENCY_UNLOCK_APPS].orEmpty(),
            persistedExpireTime = snapshot[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME],
        )
    }
}

private data class CompletedUnlockFixture(
    val completed: EmergencyUnlockRequestResult.Completed,
    val analytics: RecordingEmergencyUnlockAnalytics,
    val stateSnapshot: EmergencyUnlockData,
    val persistedApps: Set<String>,
    val persistedExpireTime: Long?,
)

private class RecordingEmergencyUnlockDao(
    private val todayCount: Int,
    private val sinceCount: Int = 0,
) : EmergencyUnlockDao {
    val inserted = mutableListOf<EmergencyUnlockEntity>()
    val countTodayCalls = mutableListOf<Long>()
    val countSinceCalls = mutableListOf<Long>()

    override suspend fun insert(entity: EmergencyUnlockEntity) {
        inserted += entity
    }

    override fun fetchByDateRange(start: Long, end: Long): Flow<List<EmergencyUnlockEntity>> = emptyFlow()

    override suspend fun countToday(todayStart: Long): Int {
        countTodayCalls += todayStart
        return todayCount
    }

    override suspend fun countSince(timestampMillis: Long): Int {
        countSinceCalls += timestampMillis
        return sinceCount
    }
}

private class RecordingEmergencyUnlockAnalytics : KeepAnalytics {
    val records = mutableListOf<AnalyticsRecord>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(name: String, value: String) = Unit

    override fun trackFirstOpen() = Unit

    override fun trackOnboardingStepView(stepName: String) = Unit

    override fun trackOnboardingStepComplete(stepName: String) = Unit

    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit

    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit

    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit

    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit

    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) {
        records += AnalyticsRecord.Used(source = source, unlockCountRemaining = unlockCountRemaining)
    }

    override fun trackEmergencyUnlockCompleted(reason: String, durationMinutes: Int, remainingUnlocks: Int) {
        records +=
            AnalyticsRecord.Completed(
                reason = reason,
                durationMinutes = durationMinutes,
                remainingUnlocks = remainingUnlocks,
            )
    }
}

private sealed interface AnalyticsRecord {
    data class Used(val source: String, val unlockCountRemaining: Int?) : AnalyticsRecord

    data class Completed(
        val reason: String,
        val durationMinutes: Int,
        val remainingUnlocks: Int,
    ) : AnalyticsRecord
}

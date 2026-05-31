package com.uiery.keep.feature.lock

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.lifecycle.SavedStateHandle
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeEmergencyUnlockDao
import com.uiery.keep.feature.review.FakeLockHistoryDao
import com.uiery.keep.feature.review.FakeReviewRemoteConfig
import com.uiery.keep.feature.review.FakeAccessibilityChecker
import com.uiery.keep.feature.review.ReviewBuildConfig
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.service.EmergencyUnlockNotificationHelper
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class LockViewModelTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-25T00:00:00Z"), ZoneId.of("UTC"))

    @Test
    fun initLogsLockScreenView() {
        val analytics = LockRecordingKeepAnalytics()
        val dataStore = FakeDataStore()
        val emergencyUnlockDao = FakeEmergencyUnlockDao()
        val reviewEligibility = ReviewEligibilityEvaluator(
            dataStore = dataStore,
            remoteConfig = FakeReviewRemoteConfig(enabled = true),
            accessibilityChecker = FakeAccessibilityChecker(enabled = true),
            emergencyUnlockDao = emergencyUnlockDao,
            lockHistoryDao = FakeLockHistoryDao(),
            clock = clock,
            buildConfig = ReviewBuildConfig(isDebug = false, flavor = "dev"),
        )

        LockViewModel(
            savedStateHandle = SavedStateHandle(mapOf("lockTime" to "2099-01-01T00:00:00", "isRoutine" to false)),
            routineDao = FakeRoutineDao(),
            lockHistoryDao = FakeLockHistoryDao(),
            dataStore = dataStore,
            emergencyUnlockCoordinator = EmergencyUnlockCoordinator(
                dataStore = dataStore,
                emergencyUnlockDao = emergencyUnlockDao,
                analytics = analytics,
            ),
            notificationHelper = Mockito.mock(EmergencyUnlockNotificationHelper::class.java),
            analytics = analytics,
            reviewEligibility = reviewEligibility,
        )

        assertEquals(listOf(KeepAnalyticsScreen.LOCK), analytics.screenViews)
    }

    @Test
    fun completedHomeTimerRecordsHistoryLedgerAtLockCompletion() = runBlocking {
        val analytics = LockRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one", "com.example.two"),
                PreferencesKey.TOTAL_BLOCK_TIME to 9_000L,
                PreferencesKey.LONG_BLOCK_TIME to 4_000L,
            ),
        )
        val emergencyUnlockDao = FakeEmergencyUnlockDao()
        val lockHistoryDao = LockRecordingHistoryDao()
        val reviewEligibility = ReviewEligibilityEvaluator(
            dataStore = dataStore,
            remoteConfig = FakeReviewRemoteConfig(enabled = true),
            accessibilityChecker = FakeAccessibilityChecker(enabled = true),
            emergencyUnlockDao = emergencyUnlockDao,
            lockHistoryDao = FakeLockHistoryDao(),
            clock = clock,
            buildConfig = ReviewBuildConfig(isDebug = false, flavor = "dev"),
        )

        LockViewModel(
            savedStateHandle = SavedStateHandle(mapOf("lockTime" to "2000-01-01T00:00:00", "isRoutine" to false)),
            routineDao = FakeRoutineDao(),
            lockHistoryDao = lockHistoryDao,
            dataStore = dataStore,
            emergencyUnlockCoordinator = EmergencyUnlockCoordinator(
                dataStore = dataStore,
                emergencyUnlockDao = emergencyUnlockDao,
                analytics = analytics,
            ),
            notificationHelper = Mockito.mock(EmergencyUnlockNotificationHelper::class.java),
            analytics = analytics,
            reviewEligibility = reviewEligibility,
        )
        delay(100)

        assertEquals(1, lockHistoryDao.inserted.size)
        val session = lockHistoryDao.inserted.single()
        assertEquals(listOf("com.example.one", "com.example.two"), session.lockedApps)
        assertEquals(false, session.isRoutine)
        assertEquals(session.endTimestamp - session.startTimestamp, session.durationMillis)
        val snapshot = dataStore.snapshot()
        assertEquals(9_000L + session.durationMillis, snapshot[PreferencesKey.TOTAL_BLOCK_TIME])
        assertEquals(maxOf(4_000L, session.durationMillis), snapshot[PreferencesKey.LONG_BLOCK_TIME])
    }
}

private class LockRecordingHistoryDao : LockHistoryDao {
    val inserted = mutableListOf<LockHistoryEntity>()

    override suspend fun insert(entity: LockHistoryEntity) {
        inserted += entity
    }

    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> = emptyFlow()

    override fun fetchAll(): Flow<List<LockHistoryEntity>> = emptyFlow()

    override suspend fun countSuccessfulSessions(): Int = inserted.size

    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int =
        inserted.count { it.startTimestamp >= timestampMillis }
}

private class FakeRoutineDao : RoutineDao {
    override fun fetchAll(): Flow<List<RoutineEntity>> = emptyFlow()
    override fun fetchAllOnce(): List<RoutineEntity> = emptyList()
    override fun fetch(id: Long): RoutineEntity = throw UnsupportedOperationException()
    override fun insert(routineEntity: RoutineEntity): Long = 0L
    override fun deleteById(id: Long) = Unit
    override fun update(routineEntity: RoutineEntity) = Unit
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}

private class LockRecordingKeepAnalytics : KeepAnalytics {
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
package com.uiery.keep.feature.lock

import androidx.lifecycle.SavedStateHandle
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
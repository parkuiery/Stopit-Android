package com.uiery.keep.feature.lock

import androidx.lifecycle.SavedStateHandle
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.feature.review.FakeAccessibilityChecker
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeEmergencyUnlockDao
import com.uiery.keep.feature.review.FakeLockHistoryDao
import com.uiery.keep.feature.review.FakeReviewRemoteConfig
import com.uiery.keep.feature.review.ReviewBuildConfig
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.service.EmergencyUnlockNotificationHelper
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class LockViewModelTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-25T09:45:00Z"), ZoneId.of("UTC"))

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
            clock = clock,
        )

        assertEquals(listOf(KeepAnalyticsScreen.LOCK), analytics.screenViews)
    }

    @Test
    fun routineLockUsesCurrentRoutineWindowStartTimeForSessionAnchor() = runBlocking {
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
        val lockHistoryDao = RecordingLockHistoryDao()
        val routine =
            RoutineEntity(
                id = 1,
                name = "Morning focus",
                startTime = LocalTime(hour = 9, minute = 0),
                endTime = LocalTime(hour = 10, minute = 0),
                repeatDays = listOf(DayOfWeek.MONDAY),
                lockApplications = listOf("com.youtube"),
                isEnabled = true,
                changeLockHours = null,
            )

        val viewModel =
            LockViewModel(
                savedStateHandle = SavedStateHandle(mapOf("lockTime" to LocalDateTime.now(clock).toString(), "isRoutine" to true)),
                routineDao = FakeRoutineDao(flowOf(listOf(routine))),
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
                clock = clock,
            )

        val expectedStartTime = LocalDateTime.of(2026, 5, 25, 9, 0).atZone(clock.zone).toInstant().toEpochMilli()

        repeat(20) {
            if (viewModel.container.stateFlow.value.routineStartTime == expectedStartTime) {
                return@repeat
            }
            delay(10)
        }

        assertEquals(expectedStartTime, viewModel.container.stateFlow.value.routineStartTime)
    }
}

private class FakeRoutineDao(
    private val routinesFlow: Flow<List<RoutineEntity>> = emptyFlow(),
) : RoutineDao {
    override fun fetchAll(): Flow<List<RoutineEntity>> = routinesFlow
    override fun fetchAllOnce(): List<RoutineEntity> = emptyList()
    override fun fetch(id: Long): RoutineEntity = throw UnsupportedOperationException()
    override fun insert(routineEntity: RoutineEntity): Long = 0L
    override fun deleteById(id: Long) = Unit
    override fun update(routineEntity: RoutineEntity) = Unit
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}

private class RecordingLockHistoryDao : LockHistoryDao {
    var inserted: LockHistoryEntity? = null

    override suspend fun insert(entity: LockHistoryEntity) {
        inserted = entity
    }

    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> = emptyFlow()
    override fun fetchAll(): Flow<List<LockHistoryEntity>> = emptyFlow()
    override suspend fun countSuccessfulSessions(): Int = 0
    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int = 0
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
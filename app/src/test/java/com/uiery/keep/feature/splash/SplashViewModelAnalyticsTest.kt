package com.uiery.keep.feature.splash

import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.routine.RoutineExactAlarmOrchestrator
import com.uiery.keep.data.routine.RoomRoutineRepository
import com.uiery.keep.feature.routine.RoutineRestoreAftercare
import com.uiery.keep.notification.RoutineScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class SplashViewModelAnalyticsTest {
    @Test
    fun initLogsSplashScreenView() {
        val analytics = RecordingSplashAnalytics()

        val dataStore = FakeDataStore()
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        val routineRepository = RoomRoutineRepository(EmptySplashRoutineDao)

        SplashViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            analytics = analytics,
            routineRestoreAftercare = RoutineRestoreAftercare(
                routineRepository = routineRepository,
                dataStore = dataStore,
                exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(scheduler),
                routineNoticeStore = RoutineNoticeStore(dataStore),
            ),
        )

        assertEquals(listOf(KeepAnalyticsScreen.SPLASH), analytics.screenViews)
    }
}

private object EmptySplashRoutineDao : RoutineDao {
    override fun fetchAll(): Flow<List<RoutineEntity>> = flowOf(emptyList())
    override fun fetchAllOnce(): List<RoutineEntity> = emptyList()
    override fun fetch(id: Long): RoutineEntity = error("No routines in splash analytics test")
    override fun insert(routineEntity: RoutineEntity): Long = routineEntity.id
    override fun deleteById(id: Long) = Unit
    override fun update(routineEntity: RoutineEntity) = Unit
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}

private class RecordingSplashAnalytics : KeepAnalytics {
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
    ) = Unit

    override fun trackFocusSummaryShareSheetOpened(
        periodType: String,
        sessionCountBucket: String,
        durationMinutesBucket: String,
    ) = Unit

    override fun trackFocusSummaryShareFailed(periodType: String, reason: String) = Unit
}

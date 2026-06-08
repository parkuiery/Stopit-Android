package com.uiery.keep.feature.splash

import androidx.datastore.preferences.core.emptyPreferences
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsUserProperty
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.feature.routine.RoutineExactAlarmOrchestrator
import com.uiery.keep.feature.routine.RoomRoutineRepository
import com.uiery.keep.feature.routine.RoutineRestoreAftercare
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toEntity
import com.uiery.keep.model.toModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import java.time.DayOfWeek

class SplashViewModelRestoreSchedulingTest {
    @Test
    fun splashStartupReschedulesRestoredRoomRoutineBeforeOnboardingNavigation() = runBlocking {
        val routine = routineEntity(id = 490L, isEnabled = true)
        val routineDao = SplashRestoreRoutineDao(listOf(routine))
        val dataStore = FakeDataStore(emptyPreferences())
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        val analytics = RecordingSplashRoutineCountAnalytics()
        Mockito.`when`(scheduler.canScheduleExactAlarms()).thenReturn(true)
        Mockito.`when`(scheduler.scheduleRoutine(routine.toModel()))
            .thenReturn(RoutineScheduleResult.Scheduled)
        val routineRepository = RoomRoutineRepository(routineDao)

        val viewModel = SplashViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            analytics = analytics,
            routineRestoreAftercare = RoutineRestoreAftercare(
                routineRepository = routineRepository,
                dataStore = dataStore,
                exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(scheduler),
                routineNoticeStore = RoutineNoticeStore(dataStore),
            ),
        )

        waitFor { analytics.userProperties.isNotEmpty() }

        Mockito.verify(scheduler, Mockito.timeout(1_000)).scheduleRoutine(routine.toModel())
        assertEquals(
            listOf(KeepAnalyticsUserProperty.ROUTINES_COUNT to "1"),
            analytics.userProperties,
        )
        assertEquals(SplashSideEffect.MoveToOnboarding, viewModel.container.sideEffectFlow.first())
    }

    private suspend fun waitFor(predicate: suspend () -> Boolean) {
        repeat(50) {
            if (predicate()) return
            delay(20)
        }
    }

    private fun routineEntity(id: Long, isEnabled: Boolean) = RoutineModel(
        id = id,
        name = "Splash restored routine $id",
        startTime = LocalTime(hour = 8, minute = 0),
        endTime = LocalTime(hour = 9, minute = 0),
        repeatDays = listOf(DayOfWeek.MONDAY).toRepeatDaysBinary(),
        lockApplications = listOf("com.example.blocked"),
        isEnabled = isEnabled,
        changeLockHours = 0,
    ).toEntity()
}

private class SplashRestoreRoutineDao(
    routines: List<RoutineEntity>,
) : RoutineDao {
    private val state = MutableStateFlow(routines)
    var fetchAllOnceCalled: Boolean = false
        private set

    override fun fetchAll(): Flow<List<RoutineEntity>> = state
    override fun fetchAllOnce(): List<RoutineEntity> {
        fetchAllOnceCalled = true
        return state.value
    }
    override fun fetch(id: Long): RoutineEntity = state.value.first { it.id == id }
    override fun insert(routineEntity: RoutineEntity): Long = routineEntity.id
    override fun deleteById(id: Long) = Unit
    override fun update(routineEntity: RoutineEntity) = Unit
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) {
        state.value = state.value.map { routine ->
            if (routine.id == id) routine.copy(isEnabled = isEnabled) else routine
        }
    }
}

private class RecordingSplashRoutineCountAnalytics : KeepAnalytics {
    val userProperties = mutableListOf<Pair<String, String>>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) {
        userProperties += name to value
    }
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}

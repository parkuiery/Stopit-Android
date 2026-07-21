package com.uiery.keep.feature.splash

import com.uiery.keep.data.routine.RoutineRepository
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsUserProperty
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.data.routine.RoutineExactAlarmOrchestrator
import com.uiery.keep.data.routine.RoomRoutineRepository
import com.uiery.keep.data.routine.RoutineRestoreAftercare
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.database.mapper.toEntity
import com.uiery.keep.database.mapper.toModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import java.time.DayOfWeek

class SplashViewModelRestoreSchedulingTest {
    @Test
    fun enabledPromiseResultSurvivesProcessDeathAndOverridesRestoredRoomHomeRoute() = runBlocking {
        assertPersistedPromiseCoachReturnsToOnboarding(
            phase = FirstPromisePhase.ResultEnabled,
            scheduleState = FirstPromiseScheduleState.Enabled,
            routineEnabled = true,
        )
    }

    @Test
    fun disabledPromiseSchedulePermissionSurvivesProcessDeathAndOverridesRestoredRoomHomeRoute() = runBlocking {
        assertPersistedPromiseCoachReturnsToOnboarding(
            phase = FirstPromisePhase.SchedulePermissionRequired,
            scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
            routineEnabled = false,
        )
    }

    @Test
    fun restoredRoomRoutineTreatsResetIsNewAsExistingUserAndMovesHome() = runBlocking {
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
            firstPromiseDraftStore = FirstPromiseDraftStore(dataStore),
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
        assertEquals(SplashSideEffect.MoveToHome, viewModel.container.sideEffectFlow.first())
        assertEquals(0, analytics.firstOpenCount)
    }

    private suspend fun waitFor(predicate: suspend () -> Boolean) {
        repeat(50) {
            if (predicate()) return
            delay(20)
        }
    }

    private suspend fun assertPersistedPromiseCoachReturnsToOnboarding(
        phase: FirstPromisePhase,
        scheduleState: FirstPromiseScheduleState,
        routineEnabled: Boolean,
    ) {
        val routine = routineEntity(id = 490L, isEnabled = routineEnabled)
        val routineDao = SplashRestoreRoutineDao(listOf(routine))
        val state = FirstPromiseOnboardingState(
            assignment = OnboardingVariant.PromiseCoachV1,
            assignmentVersion = OnboardingAssignmentVersion.V1,
            phase = phase,
            routineId = 490L,
            scheduleState = scheduleState,
        )
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.IS_NEW to true,
                PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
            ),
        )
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(scheduler.canScheduleExactAlarms()).thenReturn(true)
        if (routineEnabled) {
            Mockito.`when`(scheduler.scheduleRoutine(routine.toModel()))
                .thenReturn(RoutineScheduleResult.Scheduled)
        }
        val analytics = RecordingSplashRoutineCountAnalytics()
        val viewModel = SplashViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            firstPromiseDraftStore = FirstPromiseDraftStore(dataStore),
            analytics = analytics,
            routineRestoreAftercare = RoutineRestoreAftercare(
                routineRepository = RoomRoutineRepository(routineDao),
                dataStore = dataStore,
                exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(scheduler),
                routineNoticeStore = RoutineNoticeStore(dataStore),
            ),
        )

        waitFor { analytics.userProperties.isNotEmpty() }

        assertEquals(SplashSideEffect.MoveToOnboarding, viewModel.container.sideEffectFlow.first())
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
    var firstOpenCount: Int = 0
        private set

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) {
        userProperties += name to value
    }
    override fun trackFirstOpen() {
        firstOpenCount += 1
    }
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}

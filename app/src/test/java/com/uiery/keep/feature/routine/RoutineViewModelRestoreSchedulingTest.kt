package com.uiery.keep.feature.routine

import androidx.datastore.preferences.core.emptyPreferences
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.datastore.RoutineStore
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toEntity
import com.uiery.keep.model.toModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.time.DayOfWeek

class RoutineViewModelRestoreSchedulingTest {
    @Test
    fun routineScreenEntryReschedulesEnabledRoutinesLoadedFromRoomAfterRestore() = runBlocking {
        val routine = routineEntity(id = 11L, isEnabled = true)
        val routineDao = RestoreSchedulingRoutineDao(listOf(routine))
        val dataStore = FakeDataStore(emptyPreferences())
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(scheduler.canScheduleExactAlarms()).thenReturn(true)
        Mockito.`when`(scheduler.scheduleRoutine(routine.toModel()))
            .thenReturn(RoutineScheduleResult.Scheduled)

        createViewModel(
            routineDao = routineDao,
            dataStore = dataStore,
            scheduler = scheduler,
        )
        waitFor { routineDao.fetchAllCollected }

        Mockito.verify(scheduler).scheduleRoutine(routine.toModel())
        assertEquals(listOf(routine.toModel()), RoutineStore(dataStore).readCachedRoutines())
        assertEquals(emptyList<Long>(), routineDao.disabledRoutineIds)
    }

    @Test
    fun routineScreenEntryDisablesEnabledRoutineAndPromptsWhenRestoreRescheduleLacksExactAlarmPermission() = runBlocking {
        val routine = routineEntity(id = 12L, isEnabled = true)
        val routineDao = RestoreSchedulingRoutineDao(listOf(routine))
        val dataStore = FakeDataStore(emptyPreferences())
        val noticeStore = RoutineNoticeStore(dataStore)
        noticeStore.markAlarmPermissionPromptShown()
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(scheduler.canScheduleExactAlarms()).thenReturn(true)
        Mockito.`when`(scheduler.scheduleRoutine(routine.toModel()))
            .thenReturn(RoutineScheduleResult.MissingExactAlarmPermission)

        val viewModel = createViewModel(
            routineDao = routineDao,
            dataStore = dataStore,
            scheduler = scheduler,
            routineNoticeStore = noticeStore,
        )
        val sideEffectDeferred = async { viewModel.container.sideEffectFlow.first() }
        waitFor { routineDao.disabledRoutineIds.contains(12L) && !noticeStore.hasShownAlarmPermissionPrompt() }

        assertEquals(listOf(12L), routineDao.disabledRoutineIds)
        assertFalse(noticeStore.hasShownAlarmPermissionPrompt())
        assertFalse(viewModel.container.stateFlow.value.routines.first { it.id == 12L }.isEnabled)
        assertEquals(RoutineSideEffect.ShowAlarmPermission, sideEffectDeferred.await())
    }

    private fun createViewModel(
        routineDao: RoutineDao,
        dataStore: FakeDataStore = FakeDataStore(emptyPreferences()),
        scheduler: RoutineScheduler,
        routineNoticeStore: RoutineNoticeStore = RoutineNoticeStore(dataStore),
    ): RoutineViewModel = RoutineViewModel(
        routineDao = routineDao,
        dataStore = dataStore,
        analytics = NoopRoutineAnalytics,
        exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(scheduler),
        routineNoticeStore = routineNoticeStore,
    )

    private suspend fun waitFor(predicate: suspend () -> Boolean) {
        repeat(50) {
            if (predicate()) return
            delay(20)
        }
    }

    private fun routineEntity(
        id: Long,
        isEnabled: Boolean,
    ) = RoutineModel(
        id = id,
        name = "Restored routine $id",
        startTime = LocalTime(hour = 8, minute = 0),
        endTime = LocalTime(hour = 9, minute = 0),
        repeatDays = listOf(DayOfWeek.MONDAY).toRepeatDaysBinary(),
        lockApplications = listOf("com.example.blocked"),
        isEnabled = isEnabled,
        changeLockHours = 0,
    ).toEntity()
}

private class RestoreSchedulingRoutineDao(
    routines: List<RoutineEntity>,
) : RoutineDao {
    private val state = MutableStateFlow(routines)
    var fetchAllCollected: Boolean = false
        private set
    val disabledRoutineIds = mutableListOf<Long>()

    override fun fetchAll(): Flow<List<RoutineEntity>> {
        fetchAllCollected = true
        return state
    }

    override fun fetchAllOnce(): List<RoutineEntity> = state.value
    override fun fetch(id: Long): RoutineEntity = state.value.first { it.id == id }
    override fun insert(routineEntity: RoutineEntity): Long = routineEntity.id
    override fun deleteById(id: Long) = Unit
    override fun update(routineEntity: RoutineEntity) = Unit
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) {
        if (!isEnabled) disabledRoutineIds += id
        state.value = state.value.map { routine ->
            if (routine.id == id) routine.copy(isEnabled = isEnabled) else routine
        }
    }
}

private object NoopRoutineAnalytics : KeepAnalytics {
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
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}

package com.uiery.keep.feature.routine

import com.uiery.keep.data.routine.RoutineRepository
import androidx.datastore.preferences.core.emptyPreferences
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.RoutineCountAnalyticsSync
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito
import java.time.DayOfWeek
import java.time.LocalDateTime

class RoutineViewModelActiveRoutineGuardTest {
    @Test
    fun activeRoutineDetailClickShowsBlockedReasonInsteadOfOpeningEditSheet() = runBlocking {
        val routine = activeRoutine()
        val repository = GuardRoutineRepository(listOf(routine))
        val viewModel = createViewModel(repository)
        waitFor { viewModel.container.stateFlow.value.routines == listOf(routine) }
        val sideEffectDeferred = async { viewModel.container.sideEffectFlow.first() }

        viewModel.getRoutineDetail(routine.id)

        assertEquals(RoutineSideEffect.ShowActiveRoutineBlocked, sideEffectDeferred.await())
        assertFalse(viewModel.container.stateFlow.value.isShowEditRoutineBottomSheet)
        assertNull(viewModel.container.stateFlow.value.selectedRoutine)
    }

    @Test
    fun activeRoutineDeleteShowsBlockedReasonAndDoesNotDeleteRoutine() = runBlocking {
        val routine = activeRoutine()
        val repository = GuardRoutineRepository(listOf(routine))
        val viewModel = createViewModel(repository)
        waitFor { viewModel.container.stateFlow.value.routines == listOf(routine) }
        val sideEffectDeferred = async { viewModel.container.sideEffectFlow.first() }

        viewModel.deleteRoutine(routine.id)

        assertEquals(RoutineSideEffect.ShowActiveRoutineBlocked, sideEffectDeferred.await())
        assertEquals(emptyList<Long>(), repository.deletedIds)
    }

    @Test
    fun inactiveRoutineDeleteClosesEditSheetOnlyAfterRepositoryDeleteSucceeds() = runBlocking {
        val routine = inactiveRoutine()
        val repository = GuardRoutineRepository(listOf(routine))
        val viewModel = createViewModel(repository)
        waitFor { viewModel.container.stateFlow.value.routines == listOf(routine) }
        val sideEffectDeferred = async { viewModel.container.sideEffectFlow.first() }

        viewModel.deleteRoutine(routine.id)

        assertEquals(RoutineSideEffect.CloseEditRoutineBottomSheet, withTimeout(1_000) { sideEffectDeferred.await() })
        assertEquals(listOf(routine.id), repository.deletedIds)
    }

    @Test
    fun activeRoutineDisableShowsBlockedReasonAndKeepsRoutineEnabled() = runBlocking {
        val routine = activeRoutine()
        val repository = GuardRoutineRepository(listOf(routine))
        val viewModel = createViewModel(repository)
        waitFor { viewModel.container.stateFlow.value.routines == listOf(routine) }
        val sideEffectDeferred = async { viewModel.container.sideEffectFlow.first() }

        viewModel.changeEnabled(routine.id, isEnabled = false)

        assertEquals(RoutineSideEffect.ShowActiveRoutineBlocked, sideEffectDeferred.await())
        assertEquals(emptyList<Pair<Long, Boolean>>(), repository.enabledUpdates)
        assertEquals(routine, repository.routines.value.single())
    }

    @Test
    fun deleteRechecksStoredRoutineAndBlocksWhenRoutineBecameProtectedAfterListLoaded() = runBlocking {
        val protectedRoutine = changeLockedRoutine()
        val staleListRoutine = protectedRoutine.copy(changeLockHours = 0)
        val repository = GuardRoutineRepository(
            initialRoutines = listOf(staleListRoutine),
            fetchedRoutines = mapOf(protectedRoutine.id to protectedRoutine),
        )
        val viewModel = createViewModel(repository)
        waitFor { viewModel.container.stateFlow.value.routines == listOf(staleListRoutine) }
        val sideEffectDeferred = async { viewModel.container.sideEffectFlow.first() }
        delay(20)

        viewModel.deleteRoutine(protectedRoutine.id)

        assertEquals(RoutineSideEffect.ShowActiveRoutineBlocked, withTimeout(1_000) { sideEffectDeferred.await() })
        assertEquals(emptyList<Long>(), repository.deletedIds)
        assertEquals(listOf(protectedRoutine.id), repository.fetchCalls)
    }

    @Test
    fun disableRechecksStoredRoutineAndBlocksWhenRoutineBecameProtectedAfterListLoaded() = runBlocking {
        val protectedRoutine = changeLockedRoutine()
        val staleListRoutine = protectedRoutine.copy(changeLockHours = 0)
        val repository = GuardRoutineRepository(
            initialRoutines = listOf(staleListRoutine),
            fetchedRoutines = mapOf(protectedRoutine.id to protectedRoutine),
        )
        val viewModel = createViewModel(repository)
        waitFor { viewModel.container.stateFlow.value.routines == listOf(staleListRoutine) }
        val sideEffectDeferred = async { viewModel.container.sideEffectFlow.first() }
        delay(20)

        viewModel.changeEnabled(protectedRoutine.id, isEnabled = false)

        assertEquals(RoutineSideEffect.ShowActiveRoutineBlocked, withTimeout(1_000) { sideEffectDeferred.await() })
        assertEquals(emptyList<Pair<Long, Boolean>>(), repository.enabledUpdates)
        assertEquals(listOf(protectedRoutine.id), repository.fetchCalls)
    }

    private fun createViewModel(repository: GuardRoutineRepository): RoutineViewModel {
        val dataStore = FakeDataStore(emptyPreferences())
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(scheduler.canScheduleExactAlarms()).thenReturn(true)
        Mockito.`when`(scheduler.scheduleRoutine(anyRoutine()))
            .thenReturn(RoutineScheduleResult.Scheduled)
        val exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(scheduler)
        return RoutineViewModel(
            routineRepository = repository,
            dataStore = dataStore,
            analytics = NoopGuardRoutineAnalytics,
            routineCountAnalyticsSync = RoutineCountAnalyticsSync(EmptyGuardRoutineDao, NoopGuardRoutineAnalytics),
            exactAlarmOrchestrator = exactAlarmOrchestrator,
            routineNoticeStore = RoutineNoticeStore(dataStore),
            routineRestoreAftercare = RoutineRestoreAftercare(
                routineRepository = repository,
                dataStore = dataStore,
                exactAlarmOrchestrator = exactAlarmOrchestrator,
                routineNoticeStore = RoutineNoticeStore(dataStore),
            ),
        )
    }

    private fun activeRoutine(): RoutineModel {
        val now = LocalDateTime.now()
        val start = now.minusMinutes(5)
        val end = now.plusMinutes(55)
        return RoutineModel(
            id = 609L,
            name = "Active routine guard",
            startTime = LocalTime(hour = start.hour, minute = start.minute),
            endTime = LocalTime(hour = end.hour, minute = end.minute),
            repeatDays = listOf(now.dayOfWeek).toRepeatDaysBinary(),
            lockApplications = listOf("com.example.blocked"),
            isEnabled = true,
            changeLockHours = 0,
        )
    }

    private fun inactiveRoutine(): RoutineModel {
        val tomorrow = LocalDateTime.now().plusDays(1)
        val start = tomorrow.withHour(9).withMinute(0)
        val end = tomorrow.withHour(10).withMinute(0)
        return RoutineModel(
            id = 611L,
            name = "Inactive routine guard",
            startTime = LocalTime(hour = start.hour, minute = start.minute),
            endTime = LocalTime(hour = end.hour, minute = end.minute),
            repeatDays = listOf(start.dayOfWeek).toRepeatDaysBinary(),
            lockApplications = listOf("com.example.blocked"),
            isEnabled = true,
            changeLockHours = 0,
        )
    }

    private fun changeLockedRoutine(): RoutineModel {
        val now = LocalDateTime.now()
        val start = now.plusMinutes(30)
        val end = now.plusMinutes(90)
        return RoutineModel(
            id = 610L,
            name = "Protected routine guard",
            startTime = LocalTime(hour = start.hour, minute = start.minute),
            endTime = LocalTime(hour = end.hour, minute = end.minute),
            repeatDays = listOf(start.dayOfWeek).toRepeatDaysBinary(),
            lockApplications = listOf("com.example.blocked"),
            isEnabled = true,
            changeLockHours = 1,
        )
    }

    private fun anyRoutine(): RoutineModel =
        Mockito.any(RoutineModel::class.java) ?: activeRoutine()

    private suspend fun waitFor(predicate: suspend () -> Boolean) {
        repeat(50) {
            if (predicate()) return
            delay(20)
        }
    }
}

private class GuardRoutineRepository(
    initialRoutines: List<RoutineModel>,
    private val fetchedRoutines: Map<Long, RoutineModel> = emptyMap(),
) : RoutineRepository {
    val routines = MutableStateFlow(initialRoutines)
    val deletedIds = mutableListOf<Long>()
    val enabledUpdates = mutableListOf<Pair<Long, Boolean>>()
    val fetchCalls = mutableListOf<Long>()

    override fun fetchAll(): Flow<List<RoutineModel>> = routines
    override suspend fun fetch(id: Long): RoutineModel {
        fetchCalls += id
        return fetchedRoutines[id] ?: routines.value.first { it.id == id }
    }

    override suspend fun deleteById(id: Long) {
        deletedIds += id
        routines.value = routines.value.filterNot { it.id == id }
    }

    override suspend fun updateIsEnabledById(id: Long, isEnabled: Boolean) {
        enabledUpdates += id to isEnabled
        routines.value = routines.value.map { routine ->
            if (routine.id == id) routine.copy(isEnabled = isEnabled) else routine
        }
    }
}

private object EmptyGuardRoutineDao : RoutineDao {
    override fun fetchAll(): Flow<List<RoutineEntity>> = flowOf(emptyList())
    override fun fetchAllOnce(): List<RoutineEntity> = emptyList()
    override fun fetch(id: Long): RoutineEntity = error("No routines in EmptyGuardRoutineDao")
    override fun insert(routineEntity: RoutineEntity): Long = routineEntity.id
    override fun deleteById(id: Long) = Unit
    override fun update(routineEntity: RoutineEntity) = Unit
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}

private object NoopGuardRoutineAnalytics : KeepAnalytics {
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

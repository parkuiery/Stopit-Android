package com.uiery.keep.feature.routine

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.time.DayOfWeek

class RoutineBottomSheetViewModelTest {
    @Test
    fun resetEditStateCopiesRoutineEnabledState() = runBlocking {
        val viewModel = createViewModel()

        viewModel.resetEditState(validRoutine(isEnabled = false))
        val state = awaitState(viewModel) { it.name == "Morning focus" }

        assertFalse(state.isEnabled)
    }

    @Test
    fun resetEditStateEnablesSaveButtonForValidExistingRoutine() = runBlocking {
        val viewModel = createViewModel()

        viewModel.resetEditState(validRoutine())
        val state = awaitState(viewModel) { it.name == "Morning focus" }

        assertTrue(state.isButtonEnable)
    }

    @Test
    fun resetEditStateDisablesSaveButtonForInvalidExistingRoutine() = runBlocking {
        val viewModel = createViewModel()

        viewModel.resetEditState(validRoutine(lockApplications = emptyList()))
        val state = awaitState(viewModel) { it.name == "Morning focus" }

        assertFalse(state.isButtonEnable)
    }

    @Test
    fun editRoutineWithDisabledRoutineUpdatesDisabledEntityAndDoesNotScheduleAlarm() = runBlocking {
        listOf(true, false).forEach { canScheduleExactAlarms ->
            val routineDao = RecordingRoutineDao()
            val routineScheduler = Mockito.mock(RoutineScheduler::class.java)
            Mockito.`when`(routineScheduler.canScheduleExactAlarms()).thenReturn(canScheduleExactAlarms)
            Mockito.`when`(routineScheduler.scheduleRoutine(anyRoutine()))
                .thenReturn(RoutineScheduleResult.Scheduled)
            val viewModel = createViewModel(
                routineDao = routineDao,
                routineScheduler = routineScheduler,
            )

            viewModel.resetEditState(validRoutine(id = 7L, isEnabled = false))
            awaitState(viewModel) { it.name == "Morning focus" }
            viewModel.editRoutine(7L)
            repeat(20) {
                if (routineDao.updatedEntity != null) return@repeat
                delay(10)
            }

            assertEquals(false, routineDao.updatedEntity?.isEnabled)
            Mockito.verify(routineScheduler).cancelRoutine(7L)
            Mockito.verify(routineScheduler, Mockito.never()).scheduleRoutine(anyRoutine())
        }
    }

    private fun createViewModel(
        routineDao: RoutineDao = RecordingRoutineDao(),
        routineScheduler: RoutineScheduler = Mockito.mock(RoutineScheduler::class.java).also {
            Mockito.`when`(it.canScheduleExactAlarms()).thenReturn(true)
        },
    ) = RoutineBottomSheetViewModel(
        routineDao = routineDao,
        routineScheduler = routineScheduler,
        analytics = NoOpKeepAnalytics(),
    )

    private suspend fun awaitState(
        viewModel: RoutineBottomSheetViewModel,
        predicate: (RoutineBottomSheetUiState) -> Boolean,
    ): RoutineBottomSheetUiState {
        repeat(20) {
            val state = viewModel.container.stateFlow.value
            if (predicate(state)) return state
            delay(10)
        }
        return viewModel.container.stateFlow.value
    }

    private fun anyRoutine(): RoutineModel =
        Mockito.any(RoutineModel::class.java) ?: validRoutine()

    private fun validRoutine(
        id: Long = 1L,
        isEnabled: Boolean = true,
        lockApplications: List<String> = listOf("com.example.blocked"),
    ) = RoutineModel(
        id = id,
        name = "Morning focus",
        startTime = LocalTime(hour = 9, minute = 0),
        endTime = LocalTime(hour = 9, minute = 30),
        repeatDays = listOf(DayOfWeek.MONDAY).toRepeatDaysBinary(),
        lockApplications = lockApplications,
        isEnabled = isEnabled,
        changeLockHours = 2,
    )
}

private class RecordingRoutineDao : RoutineDao {
    var updatedEntity: RoutineEntity? = null

    override fun fetchAll(): Flow<List<RoutineEntity>> = emptyFlow()
    override fun fetchAllOnce(): List<RoutineEntity> = emptyList()
    override fun fetch(id: Long): RoutineEntity = throw UnsupportedOperationException()
    override fun insert(routineEntity: RoutineEntity): Long = routineEntity.id
    override fun deleteById(id: Long) = Unit

    override fun update(routineEntity: RoutineEntity) {
        updatedEntity = routineEntity
    }

    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}

private class NoOpKeepAnalytics : KeepAnalytics {
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

package com.uiery.keep.feature.routine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.datastore.RoutineStore
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toEntity
import com.uiery.keep.model.toModel
import com.uiery.keep.util.isChangeLocked
import com.uiery.keep.util.isRunningNow
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class RoutineViewModel
    @Inject
    constructor(
        private val routineDao: RoutineDao,
        @KeepDataSource private val dataStore: DataStore<Preferences>,
        private val analytics: KeepAnalytics,
        private val exactAlarmOrchestrator: RoutineExactAlarmOrchestrator,
        private val routineNoticeStore: RoutineNoticeStore,
    ) : ViewModel(),
        ContainerHost<RoutineUiState, RoutineSideEffect> {
        override val container: Container<RoutineUiState, RoutineSideEffect> = container(RoutineUiState())

        init {
            getRoutines()
        }

        internal fun showRoutineBottomSheet() =
            intent {
                reduce { state.copy(isShowRoutineBottomSheet = true) }
            }

        internal fun hideRoutineBottomSheet() =
            intent {
                reduce { state.copy(isShowRoutineBottomSheet = false) }
            }

        private fun showEditRoutineBottomSheet(routine: RoutineModel) =
            intent {
                reduce {
                    state.copy(
                        isShowEditRoutineBottomSheet = true,
                        selectedRoutine = routine,
                    )
                }
            }

        internal fun hideEditRoutineBottomSheet() =
            intent {
                reduce {
                    state.copy(
                        isShowEditRoutineBottomSheet = false,
                        selectedRoutine = null,
                    )
                }
            }

        internal fun getRoutineDetail(id: Long) =
            intent {
                runCatching {
                    routineDao.fetch(id)
                }.onSuccess {
                    val routine = it.toModel()
                    if (routine.isRunningNow() || routine.isChangeLocked()) {
                        return@onSuccess
                    }
                    showEditRoutineBottomSheet(routine)
                }
            }

        private fun getRoutines() =
            intent {
                routineDao.fetchAll().collect { routines ->
                    val routinesModel = routines.map { it.toModel() }
                    reduce { state.copy(routines = routinesModel) }
                    storeRoutine(routines.map { it.toModel() })
                    analytics.setUserProperty("routines_count", routines.size.toString())
                }
            }

        internal fun addRoutine(routineModel: RoutineModel) =
            intent {
                val resolvedRoutine = exactAlarmOrchestrator.resolveBeforePersist(routineModel)
                val insertedId = routineDao.insert(resolvedRoutine.routine.toEntity())
                val routineWithId = resolvedRoutine.routine.copy(id = insertedId)
                val scheduleDecision = exactAlarmOrchestrator.scheduleEnabledRoutine(routineWithId)
                if (scheduleDecision.routine != routineWithId) {
                    routineDao.update(scheduleDecision.routine.toEntity())
                }
                if (resolvedRoutine.shouldShowPermissionPrompt || scheduleDecision.shouldShowPermissionPrompt) {
                    postSideEffect(RoutineSideEffect.ShowAlarmPermission)
                }
                analyticsAddRoutine()

                // Show the newly created routine in edit bottom sheet
                if (!scheduleDecision.routine.isRunningNow()) {
                    reduce {
                        state.copy(
                            isShowEditRoutineBottomSheet = true,
                            selectedRoutine = scheduleDecision.routine,
                        )
                    }
                }
            }

        internal fun updateRoutine(routineModel: RoutineModel) =
            intent {
                val resolvedRoutine = exactAlarmOrchestrator.resolveBeforePersist(routineModel)
                routineDao.update(resolvedRoutine.routine.toEntity())
                exactAlarmOrchestrator.cancelRoutine(routineModel.id)
                val scheduleDecision = exactAlarmOrchestrator.scheduleEnabledRoutine(resolvedRoutine.routine)
                if (scheduleDecision.routine != resolvedRoutine.routine) {
                    routineDao.update(scheduleDecision.routine.toEntity())
                }
                if (resolvedRoutine.shouldShowPermissionPrompt || scheduleDecision.shouldShowPermissionPrompt) {
                    postSideEffect(RoutineSideEffect.ShowAlarmPermission)
                }
            }

        internal fun deleteRoutine(id: Long) =
            intent {
                val routine = state.routines.find { it.id == id }
                if (routine?.isRunningNow() == true || routine?.isChangeLocked() == true) {
                    return@intent
                }
                exactAlarmOrchestrator.cancelRoutine(id)
                routineDao.deleteById(id)
            }

        internal fun changeEnabled(
            id: Long,
            isEnabled: Boolean,
        ) = intent {
            val routine = state.routines.find { it.id == id }
            val isRunningRoutine = routine?.isRunningNow() == true

            if (!isEnabled && isRunningRoutine) {
                return@intent
            }

            if (routine?.isChangeLocked() == true) {
                return@intent
            }

            routine?.let {
                val resolvedRoutine = exactAlarmOrchestrator.resolveBeforePersist(it.copy(isEnabled = isEnabled))
                routineDao.updateIsEnabledById(id, resolvedRoutine.routine.isEnabled)
                val scheduleDecision = exactAlarmOrchestrator.scheduleEnabledRoutine(resolvedRoutine.routine)
                if (scheduleDecision.routine.isEnabled != resolvedRoutine.routine.isEnabled) {
                    routineDao.updateIsEnabledById(id, scheduleDecision.routine.isEnabled)
                }
                if (!resolvedRoutine.routine.isEnabled) {
                    exactAlarmOrchestrator.cancelRoutine(id)
                }
                if (resolvedRoutine.shouldShowPermissionPrompt || scheduleDecision.shouldShowPermissionPrompt) {
                    postSideEffect(RoutineSideEffect.ShowAlarmPermission)
                }
            }
        }

        private fun storeRoutine(routines: List<RoutineModel>) =
            intent {
                RoutineStore(dataStore).writeCachedRoutines(routines)
            }

        fun analyticsRoutineScreen() =
            intent {
                analytics.logScreenView(KeepAnalyticsScreen.ROUTINE)
            }

        private fun analyticsAddRoutine() =
            intent {
                analytics.logEvent("add_routine")
            }

        internal fun checkAlarmPermissionNeeded() =
            intent {
                val hasShown = routineNoticeStore.hasShownAlarmPermissionPrompt()
                if (!hasShown && state.routines.isNotEmpty() && !exactAlarmOrchestrator.canScheduleExactAlarms()) {
                    postSideEffect(RoutineSideEffect.ShowAlarmPermission)
                }
            }

        internal fun markAlarmPermissionShown() =
            intent {
                routineNoticeStore.markAlarmPermissionPromptShown()
            }
    }

data class RoutineUiState(
    val isShowRoutineBottomSheet: Boolean = false,
    val isShowEditRoutineBottomSheet: Boolean = false,
    val routines: List<RoutineModel> = emptyList(),
    val selectedRoutine: RoutineModel? = null,
)

sealed class RoutineSideEffect {
    data class MoveToLock(
        val lockTime: String?,
        val isRoutine: Boolean,
    ) : RoutineSideEffect()

    data object ShowAlarmPermission : RoutineSideEffect()
}

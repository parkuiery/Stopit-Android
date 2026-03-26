package com.uiery.keep.feature.routine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toEntity
import com.uiery.keep.model.toModel
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.util.isChangeLocked
import com.uiery.keep.util.isRunningNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
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
        private val analytics: FirebaseAnalytics,
        private val routineScheduler: RoutineScheduler,
    ) : ViewModel(),
        ContainerHost<RoutineUiState, RoutineSideEffect> {
        override val container: Container<RoutineUiState, RoutineSideEffect> = container(RoutineUiState())

        // val routineService = Retrofit.routineService

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
                    // routineService.getDetailRoutine(id)
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
//        runCatching {
//            routineService.getAllRoutines(
//                deviceId = deviceId(),
//            )
//        }.onSuccess {
//            reduce { state.copy(routines = it) }
//        }.onFailure {
//            Log.d("RoutineViewMode",it.toString())
//        }
            }

        internal fun addRoutine(routineModel: RoutineModel) =
            intent {
                val insertedId = routineDao.insert(routineModel.toEntity())
                val routineWithId = routineModel.copy(id = insertedId)
                if (routineModel.isEnabled) {
                    routineScheduler.scheduleRoutine(routineWithId)
                }
                analyticsAddRoutine()

                // Show the newly created routine in edit bottom sheet
                if (!routineWithId.isRunningNow()) {
                    reduce {
                        state.copy(
                            isShowEditRoutineBottomSheet = true,
                            selectedRoutine = routineWithId,
                        )
                    }
                }
            }

        internal fun updateRoutine(routineModel: RoutineModel) =
            intent {
                routineDao.update(routineModel.toEntity())
                routineScheduler.cancelRoutine(routineModel.id)
                if (routineModel.isEnabled) {
                    routineScheduler.scheduleRoutine(routineModel)
                }
            }

        internal fun deleteRoutine(id: Long) =
            intent {
                val routine = state.routines.find { it.id == id }
                if (routine?.isRunningNow() == true || routine?.isChangeLocked() == true) {
                    return@intent
                }
                routineScheduler.cancelRoutine(id)
                routineDao.deleteById(id)
//        runCatching {
//            routineService.deleteRoutine(id)
//        }.onSuccess {
//            val routine = state.routines.find { it.id == id }
//            routine?.let {
//                reduce { state.copy(routines = state.routines.minus(it)) }
//            }
//        }
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

            routineDao.updateIsEnabledById(id, isEnabled)
            routine?.let {
                if (isEnabled) {
                    routineScheduler.scheduleRoutine(it.copy(isEnabled = true))
                } else {
                    routineScheduler.cancelRoutine(id)
                }
            }
//        val previousRoutines = state.routines
//        val updatedRoutines = state.routines.map {
//            if (it.id == id) it.copy(isEnabled = isEnabled) else it
//        }
//        reduce { state.copy(routines = updatedRoutines) }
//         runCatching {
//             routineService.turnRoutine(
//                 id = id,
//                 turnRoutineRequest = TurnRoutineRequest(isEnabled)
//             )
//         }.onFailure {
//             reduce { state.copy(routines = previousRoutines) }
//         }
        }

        private fun storeRoutine(routines: List<RoutineModel>) =
            intent {
                dataStore.edit { preferences ->
                    preferences[PreferencesKey.ROUTINES] = Json.encodeToString(routines)
                }
            }

        fun analyticsRoutineScreen() =
            intent {
                analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                    param(FirebaseAnalytics.Param.SCREEN_NAME, "RoutineScreen")
                }
            }

        private fun analyticsAddRoutine() =
            intent {
                analytics.logEvent("add_routine") { }
            }

        internal fun checkAlarmPermissionNeeded() =
            intent {
                val preferences = dataStore.data.first()
                val hasShown = preferences[PreferencesKey.HAS_SHOWN_ALARM_PERMISSION] ?: false
                if (!hasShown && state.routines.isNotEmpty()) {
                    postSideEffect(RoutineSideEffect.ShowAlarmPermission)
                }
            }

        internal fun markAlarmPermissionShown() =
            intent {
                dataStore.edit { preferences ->
                    preferences[PreferencesKey.HAS_SHOWN_ALARM_PERMISSION] = true
                }
            }
    }

data class RoutineUiState(
    val isShowRoutineBottomSheet: Boolean = false,
    val isShowEditRoutineBottomSheet: Boolean = false,
    val routines: List<RoutineModel> = emptyList(),
    val selectedRoutine: RoutineModel? = null, // 추가
)

sealed class RoutineSideEffect {
    data class MoveToLock(
        val lockTime: String?,
        val isRoutine: Boolean,
    ) : RoutineSideEffect()

    data object ShowAlarmPermission : RoutineSideEffect()
}

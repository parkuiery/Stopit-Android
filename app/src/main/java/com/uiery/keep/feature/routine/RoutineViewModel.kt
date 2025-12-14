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
import com.uiery.keep.util.toDayOfWeekList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val analytics: FirebaseAnalytics,
    ): ContainerHost<RoutineUiState,RoutineSideEffect>, ViewModel(){
    override val container: Container<RoutineUiState, RoutineSideEffect> = container(RoutineUiState())

    //val routineService = Retrofit.routineService

    init {
        getRoutines()
    }

    internal fun showRoutineBottomSheet() = intent {
        reduce { state.copy(isShowRoutineBottomSheet = true) }
    }

    internal fun hideRoutineBottomSheet() = intent {
        reduce { state.copy(isShowRoutineBottomSheet = false) }
    }

    private fun showEditRoutineBottomSheet(routine: RoutineModel) = intent {
        reduce {
            state.copy(
                isShowEditRoutineBottomSheet = true,
                selectedRoutine = routine
            )
        }
    }

    internal fun hideEditRoutineBottomSheet() = intent {
        reduce {
            state.copy(
                isShowEditRoutineBottomSheet = false,
                selectedRoutine = null
            )
        }
    }

    internal fun getRoutineDetail(id: Long) = intent {
        runCatching {
            //routineService.getDetailRoutine(id)
            routineDao.fetch(id)
        }.onSuccess {
            showEditRoutineBottomSheet(it.toModel())
        }
    }

    private fun getRoutines() = intent {
        routineDao.fetchAll().collect{ routines ->
            val routinesModel = routines.map { it.toModel() }
            reduce { state.copy(routines = routinesModel) }
            checkRoutine(routinesModel)
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

    private fun checkRoutine(routines: List<RoutineModel>) = intent {
        val isRoutine = routines.filter { it.isEnabled }
            .filter { it.repeatDays.toDayOfWeekList().contains(LocalDateTime.now().dayOfWeek) }
            .any {
                it.startTime.rangeUntil(it.endTime)
                    .contains(LocalDateTime.now().toKotlinLocalDateTime().time)
            }

        if(isRoutine) postSideEffect(RoutineSideEffect.MoveToLock(null,true))
    }

    internal fun addRoutine(routineModel: RoutineModel) = intent {
        routineDao.insert(routineModel.toEntity())
        analyticsAddRoutine()
    }

    internal fun updateRoutine(routineModel: RoutineModel) = intent {
        routineDao.update(routineModel.toEntity())
    }

    internal fun deleteRoutine(id: Long) = intent {
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

    internal fun changeEnabled(id: Long,isEnabled: Boolean) = intent {
        routineDao.updateIsEnabledById(id,isEnabled)
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

    private fun storeRoutine(routines: List<RoutineModel>) = intent {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.ROUTINES] = Json.encodeToString(routines)
        }
    }

    fun analyticsRoutineScreen() = intent {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, "RoutineScreen")
        }
    }

    private fun analyticsAddRoutine() = intent {
        analytics.logEvent("add_routine") { }
    }
}

data class RoutineUiState(
    val isShowRoutineBottomSheet: Boolean = false,
    val isShowEditRoutineBottomSheet: Boolean = false,
    val routines: List<RoutineModel> = emptyList(),
    val selectedRoutine: RoutineModel? = null, // 추가
)

sealed class RoutineSideEffect {
    data class MoveToLock(val lockTime: String?, val isRoutine: Boolean) : RoutineSideEffect()
}
package com.uiery.keep.feature.lock

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toModel
import com.uiery.keep.util.toDayOfWeekList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.atDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineDao: RoutineDao,
    private val lockHistoryDao: LockHistoryDao,
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) : ContainerHost<LockUiState,LockSideEffect>, ViewModel(){

    private val route = savedStateHandle.toRoute<LockRoute>()
    override val container: Container<LockUiState, LockSideEffect> = container(LockUiState(
        lockTime = if(route.lockTime == null) LocalDateTime.now() else LocalDateTime.parse(route.lockTime),
        isRoutine = route.isRoutine
    ))

    init {
        initIntent()
    }

    private fun initIntent() = intent {
        getSelectedApp()
        if(route.isRoutine) getRoutines() else navigateHome(state.lockTime)
    }

    private fun getSelectedApp() = intent {
        val selectedAppPackage = dataStore.data.map { data ->
            data[PreferencesKey.SELECTED_APP_PACKAGES].orEmpty()
        }.firstOrNull()
        selectedAppPackage?.let {
            reduce { state.copy(selectedAppPackage = it) }
        }
    }

    private fun getRoutines() = intent {
        val routineStartTime = System.currentTimeMillis()
        val routines = routineDao.fetchAll().firstOrNull()?.map { it.toModel() }
        val activeRoutines = routines?.filter { it.isEnabled }
            ?.filter { it.repeatDays.toDayOfWeekList().contains(LocalDateTime.now().dayOfWeek) }
            ?.filter {
                it.startTime.rangeUntil(it.endTime)
                    .contains(LocalDateTime.now().toKotlinLocalDateTime().time)
            }
        val endTime = activeRoutines?.maxOfOrNull { it.endTime }?.atDate(LocalDateTime.now().toKotlinLocalDateTime().date)?.toJavaLocalDateTime() ?: LocalDateTime.now()
        val applications = activeRoutines?.firstOrNull()?.lockApplications ?: emptyList()
        reduce { state.copy(routines = activeRoutines.orEmpty(), selectedAppPackage = applications.toSet(), lockTime = endTime, routineStartTime = routineStartTime) }
        navigateHome(endTime)
    }

    private fun navigateHome(lockTime: LocalDateTime) = intent {
        val now = LocalDateTime.now()
        val duration = Duration.between(now, lockTime).coerceAtLeast(Duration.ZERO)
        delay(duration.toMillis())
        if (state.isRoutine) {
            saveRoutineLockHistory()
        }
        postSideEffect(LockSideEffect.MoveToHome)
    }

    private fun saveRoutineLockHistory() = intent {
        val endTime = System.currentTimeMillis()
        val startTime = state.routineStartTime
        val durationMillis = endTime - startTime

        val longBlockTime = dataStore.data.map { it[PreferencesKey.LONG_BLOCK_TIME] ?: 0L }.firstOrNull() ?: 0L
        val totalBlockTime = dataStore.data.map { it[PreferencesKey.TOTAL_BLOCK_TIME] ?: 0L }.firstOrNull() ?: 0L

        dataStore.edit { preferences ->
            preferences[PreferencesKey.LONG_BLOCK_TIME] = maxOf(longBlockTime, durationMillis)
            preferences[PreferencesKey.TOTAL_BLOCK_TIME] = totalBlockTime + durationMillis
        }

        lockHistoryDao.insert(
            LockHistoryEntity(
                startTimestamp = startTime,
                endTimestamp = endTime,
                durationMillis = durationMillis,
                lockedApps = state.selectedAppPackage.toList(),
                isRoutine = true,
            )
        )
    }
}

data class LockUiState(
    val lockTime: LocalDateTime = LocalDateTime.now(),
    val selectedAppPackage: Set<String> = emptySet(),
    val isRoutine: Boolean = false,
    val routines: List<RoutineModel> = emptyList(),
    val routineStartTime: Long = 0L,
)

sealed class LockSideEffect {
    data object MoveToHome: LockSideEffect()
}
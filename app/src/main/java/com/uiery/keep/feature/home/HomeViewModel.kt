package com.uiery.keep.feature.home

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.model.toModel
import com.uiery.keep.util.timeNow
import com.uiery.keep.util.toDayOfWeekList
import com.uiery.keep.util.today
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toKotlinLocalTime
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val analytics: FirebaseAnalytics,
    private val routineDao: RoutineDao,
    ) : ContainerHost<HomeUiState, HomeSideEffect>, ViewModel() {
    override val container: Container<HomeUiState, HomeSideEffect> = container(HomeUiState())

    init {
        getIsKeep()
        getSelectedApp()
    }

    internal fun changeIsKeep() = intent {
        val isKeep = !state.isKeep
        if (isKeep) {
            storeStartTime()
        } else {
            storeBlockTime(System.currentTimeMillis() - state.startTime)
        }
        reduce { state.copy(isKeep = isKeep, startTime = System.currentTimeMillis()) }
        storeIsKeep()
    }

    internal fun showSnackBar(message: String) = intent {
        postSideEffect(HomeSideEffect.ShowSnackBar(message))
        CoroutineScope(Dispatchers.IO).launch {
            reduce { state.copy(snackbarMessage = message) }
        }
    }

    internal fun showCategoryBottomSheet() = intent {
        reduce {
            state.copy(
                isShowCategoryBottomSheet = true,
            )
        }
    }

    internal fun showTimeBottomSheet() = intent {
        reduce {
            state.copy(isShowTimeBottomSheet = true)
        }
    }

    internal fun hideCategoryBottomSheet() = intent {
        reduce { state.copy(isShowCategoryBottomSheet = false)}
    }

    internal fun hideTimeBottomSheet() = intent {
        reduce { state.copy(isShowTimeBottomSheet = false) }
    }

    internal fun moveToLock() = intent {
        val lockTime = state.blockTime.atDate(today).toString()
        postSideEffect(HomeSideEffect.MoveToLock(lockTime,false))
    }

    private fun getSelectedApp() = intent {
        val selectedAppPackage = dataStore.data.map { data ->
            data[PreferencesKey.SELECTED_APP_PACKAGES].orEmpty()
        }.firstOrNull()
        selectedAppPackage?.let {
            reduce { state.copy(selectedAppPackage = it) }
        }
    }

    private fun storeSelectedApp(selectedAppPackage: Set<String>) = intent {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.SELECTED_APP_PACKAGES] = selectedAppPackage
        }
    }

    private fun storeBlockTime(lockedMillis: Long) = intent {
        val longBlockTime = dataStore.data.map { data ->
            data[PreferencesKey.LONG_BLOCK_TIME] ?: 0L
        }.firstOrNull() ?: 0L

        val totalBlockTime = dataStore.data.map { data ->
            data[PreferencesKey.TOTAL_BLOCK_TIME] ?: 0L
        }.firstOrNull() ?: 0L

        val newLongBlockTime = maxOf(longBlockTime, lockedMillis)
        val newTotalBlockTime = totalBlockTime + lockedMillis

        dataStore.edit { preferences ->
            preferences[PreferencesKey.LONG_BLOCK_TIME] = newLongBlockTime
            preferences[PreferencesKey.TOTAL_BLOCK_TIME] = newTotalBlockTime
        }
    }

    internal fun selectCategoryComplete(selectedAppPackage: Set<String>) = intent {
        storeSelectedApp(selectedAppPackage)
        reduce { state.copy(selectedAppPackage = selectedAppPackage) }
    }

    private fun storeIsKeep() = intent {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.IS_KEEP] = state.isKeep
        }
    }

    private fun storeStartTime() = intent {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.START_TIME] = System.currentTimeMillis()
        }
    }

    private fun getStartTime() = intent {
        val startTime = dataStore.data.map { preferences ->
            preferences[PreferencesKey.START_TIME]
        }.firstOrNull()
        reduce { state.copy(startTime = startTime ?: System.currentTimeMillis()) }
    }

    private fun getIsKeep() = intent {
        val isKeep = dataStore.data.map { preferences ->
            preferences[PreferencesKey.IS_KEEP]
        }.firstOrNull()
        reduce { state.copy(isKeep = isKeep == true) }
        if (isKeep == true) {
            getStartTime()
        }
    }

    internal fun checkRoutines() = intent {
        val routines = routineDao.fetchAll().firstOrNull()?.map { it.toModel() }
        val isRoutine = routines?.filter { it.isEnabled }
            ?.filter { it.repeatDays.toDayOfWeekList().contains(LocalDateTime.now().dayOfWeek) }
            ?.filter { it.startTime.rangeUntil(it.endTime).contains(LocalDateTime.now().toKotlinLocalDateTime().time) }?.isNotEmpty() == true

        if(isRoutine) postSideEffect(HomeSideEffect.MoveToLock(null,true))
    }

    internal fun updateCountdownTime(countdownTime: LocalTime) = intent {
        val blockTime = timeNow.toJavaLocalTime().plusHours(countdownTime.hour.toLong()).plusMinutes(countdownTime.minute.toLong()).toKotlinLocalTime()
        reduce { state.copy(countdownTime = countdownTime,blockTime = blockTime) }
    }

    internal fun updateTimerTime(timerTime: LocalTime) = intent {
        reduce { state.copy(timerTime = timerTime, blockTime = timerTime) }
    }

    internal fun lockTime() = intent {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.LOCK_TIME] = state.blockTime.atDate(today).toString()
        }
        val lockedDuration = state.blockTime.atDate(today).toJavaLocalDateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
        storeBlockTime(lockedDuration)
    }

    internal fun analyticsHomeScreen() = intent {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, "HomeScreen")
        }
    }
}

data class HomeUiState(
    val isKeep: Boolean = false,
    val snackbarMessage: String = "",
    val isShowCategoryBottomSheet: Boolean = false,
    val isShowTimeBottomSheet: Boolean = false,
    val selectedAppPackage: Set<String> = emptySet(),
    val startTime: Long = System.currentTimeMillis(),
    val searchContent: String = "",
    val isSelectAll: Boolean  = true,
    val blockTime: LocalTime = timeNow,
    val countdownTime: LocalTime = timeNow,
    val timerTime: LocalTime = timeNow,
)

sealed class HomeSideEffect {
    data class ShowSnackBar(val message: String): HomeSideEffect()
    data class MoveToLock(val lockTime: String?,val isRoutine: Boolean): HomeSideEffect()
}
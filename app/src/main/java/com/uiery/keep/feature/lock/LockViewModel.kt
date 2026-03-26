package com.uiery.keep.feature.lock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toModel
import com.uiery.keep.service.EmergencyUnlockData
import com.uiery.keep.service.EmergencyUnlockNotificationHelper
import com.uiery.keep.service.EmergencyUnlockState
import com.uiery.keep.util.currentRoutineWindowEndDateTime
import com.uiery.keep.util.isRoutineActiveNow
import com.uiery.keep.util.toDayOfWeekList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.Duration
import java.time.LocalDateTime
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class LockViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val routineDao: RoutineDao,
        private val lockHistoryDao: LockHistoryDao,
        @KeepDataSource private val dataStore: DataStore<Preferences>,
        private val emergencyUnlockDao: EmergencyUnlockDao,
        private val notificationHelper: EmergencyUnlockNotificationHelper,
    ) : ViewModel(),
        ContainerHost<LockUiState, LockSideEffect> {
        private val route = savedStateHandle.toRoute<LockRoute>()
        override val container: Container<LockUiState, LockSideEffect> =
            container(
                LockUiState(
                    lockTime = if (route.lockTime == null) LocalDateTime.now() else LocalDateTime.parse(route.lockTime),
                    isRoutine = route.isRoutine,
                ),
            )

        private var navigateHomeJob: kotlinx.coroutines.Job? = null

        init {
            initIntent()
        }

        private fun initIntent() =
            intent {
                getSelectedApp()
                checkDailyLimit()
                if (route.isRoutine) getRoutines() else navigateHome(state.lockTime)
            }

        private fun getSelectedApp() =
            intent {
                val selectedAppPackage =
                    dataStore.data
                        .map { data ->
                            data[PreferencesKey.SELECTED_APP_PACKAGES].orEmpty()
                        }.firstOrNull()
                selectedAppPackage?.let {
                    reduce { state.copy(selectedAppPackage = it) }
                }
            }

        private fun getRoutines() =
            intent {
                val routineStartTime = System.currentTimeMillis()
                val routines = routineDao.fetchAll().firstOrNull()?.map { it.toModel() }
                val activeRoutines =
                    routines?.filter { routine ->
                        routine.isEnabled &&
                            isRoutineActiveNow(
                                startTime = routine.startTime,
                                endTime = routine.endTime,
                                repeatDays = routine.repeatDays.toDayOfWeekList(),
                            )
                    }
                val endTime =
                    activeRoutines
                        ?.maxOfOrNull { currentRoutineWindowEndDateTime(it.startTime, it.endTime) }
                        ?: LocalDateTime.now()
                val applications = activeRoutines?.firstOrNull()?.lockApplications ?: emptyList()
                reduce {
                    state.copy(
                        routines = activeRoutines.orEmpty(),
                        selectedAppPackage = applications.toSet(),
                        lockTime = endTime,
                        routineStartTime = routineStartTime,
                    )
                }
                navigateHome(endTime)
            }

        private fun navigateHome(lockTime: LocalDateTime) {
            navigateHomeJob = intent {
                val now = LocalDateTime.now()
                val duration = Duration.between(now, lockTime).coerceAtLeast(Duration.ZERO)
                delay(duration.toMillis())
                if (state.isRoutine) {
                    saveRoutineLockHistory()
                }
                postSideEffect(LockSideEffect.MoveToHome)
            }
        }

        private fun saveRoutineLockHistory() =
            intent {
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
                    ),
                )
            }

        private fun checkDailyLimit() = intent {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayStart = calendar.timeInMillis
            val count = emergencyUnlockDao.countToday(todayStart)
            reduce { state.copy(dailyLimitReached = count >= 3) }
        }

        internal fun showEmergencyUnlockSheet() = intent {
            reduce { state.copy(isShowEmergencyUnlockSheet = true) }
        }

        internal fun hideEmergencyUnlockSheet() = intent {
            reduce { state.copy(isShowEmergencyUnlockSheet = false) }
        }

        internal fun emergencyUnlock(
            reason: String,
            customReason: String?,
            apps: Set<String>,
            durationMinutes: Int,
        ) = intent {
            val now = System.currentTimeMillis()
            val expireTime = now + durationMinutes * 60_000L

            // 1. Update in-memory singleton atomically
            EmergencyUnlockState.current = EmergencyUnlockData(
                unlockedApps = apps,
                expireTimeMillis = expireTime,
            )

            // 2. Persist to DataStore
            dataStore.edit { preferences ->
                preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS] = apps
                preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] = expireTime
            }

            // 3. Save history to Room
            emergencyUnlockDao.insert(
                EmergencyUnlockEntity(
                    timestamp = now,
                    reason = reason,
                    customReason = customReason,
                    unlockedApps = apps.toList(),
                    durationMinutes = durationMinutes,
                )
            )

            // 4. Refresh daily limit count
            checkDailyLimit()

            // 5. Stay on LockScreen, start countdown
            val totalSeconds = durationMinutes * 60
            reduce {
                state.copy(
                    isEmergencyUnlockActive = true,
                    emergencyUnlockRemainingSeconds = totalSeconds,
                    emergencyUnlockedApps = apps,
                )
            }
            startEmergencyUnlockCountdown(totalSeconds)
        }

        private fun startEmergencyUnlockCountdown(totalSeconds: Int) = intent {
            var remaining = totalSeconds
            notificationHelper.showCountdown(remaining, totalSeconds)
            while (remaining > 0) {
                delay(1000)
                remaining--
                reduce { state.copy(emergencyUnlockRemainingSeconds = remaining) }
                notificationHelper.showCountdown(remaining, totalSeconds)
            }
            // Expired
            notificationHelper.showExpired()
            reduce {
                state.copy(
                    isEmergencyUnlockActive = false,
                    emergencyUnlockRemainingSeconds = 0,
                    emergencyUnlockedApps = emptySet(),
                )
            }
        }
    }

data class LockUiState(
    val lockTime: LocalDateTime = LocalDateTime.now(),
    val selectedAppPackage: Set<String> = emptySet(),
    val isRoutine: Boolean = false,
    val routines: List<RoutineModel> = emptyList(),
    val routineStartTime: Long = 0L,
    val isShowEmergencyUnlockSheet: Boolean = false,
    val dailyLimitReached: Boolean = false,
    val isEmergencyUnlockActive: Boolean = false,
    val emergencyUnlockRemainingSeconds: Int = 0,
    val emergencyUnlockedApps: Set<String> = emptySet(),
)

sealed class LockSideEffect {
    data object MoveToHome : LockSideEffect()
}

package com.uiery.keep.feature.lock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.AnalyticsEndReason
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.ReviewEligibilityDecision
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toModel
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.service.EmergencyUnlockNotificationHelper
import com.uiery.keep.service.EmergencyUnlockRequestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LockViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val routineDao: RoutineDao,
        private val lockHistoryDao: LockHistoryDao,
        @KeepDataSource private val dataStore: DataStore<Preferences>,
        private val emergencyUnlockCoordinator: EmergencyUnlockCoordinator,
        private val notificationHelper: EmergencyUnlockNotificationHelper,
        private val analytics: KeepAnalytics,
        private val reviewEligibility: ReviewEligibilityEvaluator,
    ) : ViewModel(),
        ContainerHost<LockUiState, LockSideEffect> {
        private val route = LockRoute(
            lockTime = savedStateHandle.get<String>("lockTime"),
            isRoutine = savedStateHandle.get<Boolean>("isRoutine") ?: false,
        )
        override val container: Container<LockUiState, LockSideEffect> =
            container(
                LockUiState(
                    lockTime = if (route.lockTime == null) LocalDateTime.now() else LocalDateTime.parse(route.lockTime),
                    isRoutine = route.isRoutine,
                    timerStartTime = System.currentTimeMillis(),
                ),
            )

        private var navigateHomeJob: kotlinx.coroutines.Job? = null

        init {
            analytics.logScreenView(KeepAnalyticsScreen.LOCK)
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
                val routines = routineDao.fetchAll().firstOrNull()?.map { it.toModel() }.orEmpty()
                val activeRoutineLockState = resolveActiveRoutineLockState(routines = routines)
                reduce {
                    state.copy(
                        routines = activeRoutineLockState.routines,
                        selectedAppPackage = activeRoutineLockState.blockedApps,
                        lockTime = activeRoutineLockState.endTime,
                        routineStartTime = routineStartTime,
                    )
                }
                navigateHome(activeRoutineLockState.endTime)
            }

        private fun navigateHome(lockTime: LocalDateTime) {
            navigateHomeJob = intent {
                val nowDateTime = LocalDateTime.now()
                val duration = Duration.between(nowDateTime, lockTime).coerceAtLeast(Duration.ZERO)
                delay(duration.toMillis())
                analytics.trackLockSessionEnd(
                    source = if (state.isRoutine) AnalyticsSource.ROUTINE else AnalyticsSource.HOME_TIMER,
                    endReason = AnalyticsEndReason.TIMER_ELAPSED,
                    isRoutine = state.isRoutine,
                )
                if (state.isRoutine) {
                    saveRoutineLockHistory()
                }
                maybeArmReviewPrompt(
                    isRoutine = state.isRoutine,
                    routineStartTime = state.routineStartTime,
                    timerStartTime = state.timerStartTime,
                )
                postSideEffect(LockSideEffect.MoveToHome)
            }
        }

        private suspend fun maybeArmReviewPrompt(
            isRoutine: Boolean,
            routineStartTime: Long,
            timerStartTime: Long,
        ) {
            val now = System.currentTimeMillis()
            val durationMillis = if (isRoutine) {
                now - routineStartTime
            } else {
                now - timerStartTime
            }
            dataStore.edit { prefs ->
                val current = prefs[PreferencesKey.SUCCESSFUL_SESSION_COUNT] ?: 0
                prefs[PreferencesKey.SUCCESSFUL_SESSION_COUNT] = current + 1
            }
            val decision = reviewEligibility.evaluate(
                nowMs = now,
                durationMillis = durationMillis,
                isRoutine = isRoutine,
                includeCurrentSuccessfulSession = true,
            )
            when (decision) {
                is ReviewEligibilityDecision.Eligible -> {
                    dataStore.edit { it[PreferencesKey.REVIEW_PENDING] = true }
                    analytics.reviewPromptEligible()
                }
                is ReviewEligibilityDecision.Ineligible -> {
                    analytics.reviewPromptSkipped(decision.reason.name)
                }
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
            val availability = emergencyUnlockCoordinator.readAvailability()
            reduce {
                state.copy(
                    emergencyUnlockEnabled = availability.enabled,
                    emergencyUnlockDailyLimit = availability.dailyLimit,
                    emergencyUnlockDurationOptions = availability.durationOptions,
                    emergencyUnlockReasonRequired = availability.reasonRequired,
                    dailyLimitReached = availability.dailyLimitReached,
                    dailyUnlockRemaining = availability.dailyUnlockRemaining,
                )
            }
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
            when (
                emergencyUnlockCoordinator.completeUnlock(
                    source = AnalyticsSource.LOCK_SCREEN,
                    reason = reason,
                    customReason = customReason,
                    apps = apps,
                    durationMinutes = durationMinutes,
                )
            ) {
                is EmergencyUnlockRequestResult.Rejected -> {
                    checkDailyLimit()
                    return@intent
                }

                is EmergencyUnlockRequestResult.Completed -> {
                    checkDailyLimit()

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
            }
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
    val timerStartTime: Long = 0L,
    val isShowEmergencyUnlockSheet: Boolean = false,
    val dailyLimitReached: Boolean = false,
    val dailyUnlockRemaining: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val emergencyUnlockEnabled: Boolean = true,
    val emergencyUnlockDailyLimit: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val emergencyUnlockDurationOptions: List<Int> = DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS,
    val emergencyUnlockReasonRequired: Boolean = true,
    val isEmergencyUnlockActive: Boolean = false,
    val emergencyUnlockRemainingSeconds: Int = 0,
    val emergencyUnlockedApps: Set<String> = emptySet(),
)

sealed class LockSideEffect {
    data object MoveToHome : LockSideEffect()
}

package com.uiery.keep.feature.lock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.AnalyticsEndReason
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.datastore.ReviewPromptStateStore

import com.uiery.keep.feature.review.ReviewEligibilityDecision
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.feature.routine.RoutineRepository
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.EmergencyUnlockAvailabilityReason
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.service.EmergencyUnlockNotificationHelper
import com.uiery.keep.service.EmergencyUnlockRequestResult
import com.uiery.keep.service.LockHistoryRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull

import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LockViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val routineRepository: RoutineRepository,
        private val lockHistoryRecorder: LockHistoryRecorder,
        @KeepDataSource private val dataStore: DataStore<Preferences>,
        private val blockingStateStore: BlockingStateStore,
        private val reviewPromptStateStore: ReviewPromptStateStore,
        private val emergencyUnlockCoordinator: EmergencyUnlockCoordinator,
        private val notificationHelper: EmergencyUnlockNotificationHelper,
        private val analytics: KeepAnalytics,
        private val reviewEligibility: ReviewEligibilityEvaluator,
        private val clock: Clock,
    ) : ViewModel(),
        ContainerHost<LockUiState, LockSideEffect> {
        private val route = LockRoute(
            lockTime = savedStateHandle.get<String>("lockTime"),
            isRoutine = savedStateHandle.get<Boolean>("isRoutine") ?: false,
        )
        override val container: Container<LockUiState, LockSideEffect> =
            container(
                LockUiState(
                    lockTime = ManualLockTimePolicy.toLocalDateTime(route.lockTime, clock.zone) ?: LocalDateTime.now(clock),
                    isRoutine = route.isRoutine,
                    timerStartTime = clock.millis(),
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
                if (route.isRoutine) {
                    getRoutines()
                } else {
                    val timerStartTime = resolveManualTimerStartTime(
                        fallbackStartTime = state.timerStartTime.takeIf { it > 0L } ?: clock.millis(),
                    )
                    reduce { state.copy(timerStartTime = timerStartTime) }
                    navigateHome(state.lockTime)
                }
            }

        private suspend fun resolveManualTimerStartTime(fallbackStartTime: Long): Long {
            val persistedStartTime = blockingStateStore.readStartTime()
            val resolvedStartTime = persistedStartTime ?: fallbackStartTime
            if (persistedStartTime == null) {
                blockingStateStore.saveStartTime(resolvedStartTime)
            }
            return resolvedStartTime
        }

        private fun getSelectedApp() =
            intent {
                val selectedAppPackages = blockingStateStore.readSelectedAppPackages()
                reduce { state.copy(selectedAppPackage = selectedAppPackages) }
            }

        private fun getRoutines() =
            intent {
                val nowDateTime = LocalDateTime.now(clock)
                val routines = routineRepository.fetchAll().firstOrNull().orEmpty()
                val activeRoutineLockState = resolveActiveRoutineLockState(routines = routines, nowDateTime = nowDateTime)
                val routineStartTime = activeRoutineLockState.startTime.atZone(clock.zone).toInstant().toEpochMilli()
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
                val nowDateTime = LocalDateTime.now(clock)
                val duration = Duration.between(nowDateTime, lockTime).coerceAtLeast(Duration.ZERO)
                delay(duration.toMillis())
                analytics.trackLockSessionEnd(
                    source = if (state.isRoutine) AnalyticsSource.ROUTINE else AnalyticsSource.HOME_TIMER,
                    endReason = AnalyticsEndReason.TIMER_ELAPSED,
                    isRoutine = state.isRoutine,
                )
                if (state.isRoutine) {
                    saveRoutineLockHistory()
                } else {
                    saveTimerLockHistory()
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
            val now = clock.millis()
            val durationMillis = if (isRoutine) {
                now - routineStartTime
            } else {
                now - timerStartTime
            }
            blockingStateStore.incrementSuccessfulSessionCount()
            val decision = reviewEligibility.evaluate(
                nowMs = now,
                durationMillis = durationMillis,
                isRoutine = isRoutine,
                includeCurrentSuccessfulSession = true,
            )
            when (decision) {
                is ReviewEligibilityDecision.Eligible -> {
                    reviewPromptStateStore.markPending()
                    analytics.reviewPromptEligible()
                }
                is ReviewEligibilityDecision.Ineligible -> {
                    analytics.reviewPromptSkipped(decision.reason.name)
                }
            }
        }

        private fun saveRoutineLockHistory() =
            intent {
                val endTime = clock.millis()
                lockHistoryRecorder.recordSession(
                    startTimestamp = state.routineStartTime,
                    endTimestamp = endTime,
                    lockedApps = state.selectedAppPackage,
                    isRoutine = true,
                )
            }

        private fun saveTimerLockHistory() =
            intent {
                val endTime = System.currentTimeMillis()
                lockHistoryRecorder.recordSession(
                    startTimestamp = state.timerStartTime,
                    endTimestamp = endTime,
                    lockedApps = state.selectedAppPackage,
                    isRoutine = false,
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
                    emergencyUnlockAvailabilityReason = availability.reason,
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
                    postSideEffect(LockSideEffect.UnlockCompleted)
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
    val emergencyUnlockAvailabilityReason: EmergencyUnlockAvailabilityReason = EmergencyUnlockAvailabilityReason.Available,
    val isEmergencyUnlockActive: Boolean = false,
    val emergencyUnlockRemainingSeconds: Int = 0,
    val emergencyUnlockedApps: Set<String> = emptySet(),
)

sealed class LockSideEffect {
    data object MoveToHome : LockSideEffect()
    data object UnlockCompleted : LockSideEffect()
}

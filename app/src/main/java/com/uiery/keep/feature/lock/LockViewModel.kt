package com.uiery.keep.feature.lock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.AnalyticsEndReason
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockCancelSource
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.appselection.BlockExemptPackageProvider
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.datastore.FirstPromisePracticeStore
import com.uiery.keep.feature.review.ReviewPromptArmer
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_ENABLED
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_SECONDS
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.EmergencyUnlockAvailabilityReason
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.service.EmergencyUnlockNotificationHelper
import com.uiery.keep.service.EmergencyUnlockRequestResult
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingAsserter
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
        private val emergencyUnlockCoordinator: EmergencyUnlockCoordinator,
        private val notificationHelper: EmergencyUnlockNotificationHelper,
        private val analytics: KeepAnalytics,
        private val reviewPromptArmer: ReviewPromptArmer,
        private val clock: Clock,
        private val firstPromisePracticeStore: FirstPromisePracticeStore,
        private val websiteBlockingAsserter: WebsiteBlockingAsserter = WebsiteBlockingAsserter.None,
        private val blockExemptPackageProvider: BlockExemptPackageProvider = BlockExemptPackageProvider.None,
    ) : ViewModel(),
        ContainerHost<LockUiState, LockSideEffect> {
        private val route = LockRoute(
            lockTime = savedStateHandle.get<String>("lockTime"),
            isRoutine = savedStateHandle.get<Boolean>("isRoutine") ?: false,
        )
        private val lockScreenEntry = route.toLockScreenEntry()
        override val container: Container<LockUiState, LockSideEffect> =
            container(
                LockUiState(
                    lockTime = ManualLockTimePolicy.toLocalDateTime(lockScreenEntry.lockTime, clock.zone) ?: LocalDateTime.now(clock),
                    isRoutine = lockScreenEntry.isRoutine,
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
                if (lockScreenEntry.isRoutine) {
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

        /**
         * 잠금 화면의 웹 차단 경고에서 권한을 다시 받았을 때 필터를 그 자리에서 세운다.
         *
         * 자동 경로는 한 번 거부한 잠금에 동의창을 다시 띄우지 않으므로(의도된 괴롭힘 방지),
         * 도는 잠금의 웹 차단을 되살릴 길은 이 경로뿐이다. 시간 잠금 사용자는 잠금 화면에
         * 머무르기 때문에 홈의 복구 버튼에 닿지 못한다.
         */
        internal fun retryWebsiteBlocking() =
            intent {
                // 마감이 과거면 넘기지 않는다. 서비스가 서자마자 스스로 멈춘다. 수동 Keep 처럼
                // 마감이 없는 잠금은 lockTime 이 현재값이라 여기서 자연히 걸러진다.
                val nowMillis = clock.millis()
                val deadlineMillis = state.lockTime
                    .atZone(clock.zone)
                    .toInstant()
                    .toEpochMilli()
                    .takeIf { it > nowMillis }

                websiteBlockingAsserter.assertAfterConsent(
                    selectedWebDomains = state.selectedWebDomains,
                    stopAtEpochMillis = deadlineMillis,
                )
            }

        private fun getSelectedApp() =
            intent {
                val selectedAppPackages = blockingStateStore.readSelectedAppPackages()
                // 루틴 잠금의 웹 대상은 루틴 자신이 들고 있다. 수동 잠금 목록을 섞으면
                // 이 루틴이 막지 않는 사이트까지 막고 있다고 말하게 된다.
                val selectedWebDomains = if (lockScreenEntry.isRoutine) {
                    emptySet()
                } else {
                    blockingStateStore.readSelectedWebDomains()
                }
                reduce {
                    state.copy(
                        selectedAppPackage = selectedAppPackages,
                        selectedWebDomains = selectedWebDomains,
                    )
                }
            }

        private fun getRoutines() =
            intent {
                val nowDateTime = LocalDateTime.now(clock)
                val routines = routineRepository.fetchAll().firstOrNull().orEmpty()
                val activeRoutineLockState = resolveActiveRoutineLockState(
                    routines = routines,
                    nowDateTime = nowDateTime,
                    exemptPackages = blockExemptPackageProvider.exemptPackages().homePackages,
                )
                val routineStartTime = activeRoutineLockState.startTime.atZone(clock.zone).toInstant().toEpochMilli()
                reduce {
                    state.copy(
                        routines = activeRoutineLockState.routines,
                        selectedAppPackage = activeRoutineLockState.blockedApps,
                        selectedWebDomains = activeRoutineLockState.blockedWebDomains,
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
                    firstPromisePracticeStore.clearToken()
                }
                maybeArmReviewPrompt(
                    isRoutine = state.isRoutine,
                    routineStartTime = state.routineStartTime,
                    timerStartTime = state.timerStartTime,
                )
                postSideEffect(LockSideEffect.MoveToHome)
            }
        }

        // 타이머/루틴 완주 경로. 완주 자체가 의도된 성공이므로 최소 지속 시간 하한을 두지 않는다.
        // 수동 종료 경로는 HomeViewModel 이 MANUAL_SESSION_MIN_MILLIS 와 함께 같은 armer 를 호출한다.
        private suspend fun maybeArmReviewPrompt(
            isRoutine: Boolean,
            routineStartTime: Long,
            timerStartTime: Long,
        ) {
            reviewPromptArmer.arm(
                sessionStartMillis = if (isRoutine) routineStartTime else timerStartTime,
                isRoutine = isRoutine,
            )
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
                    emergencyUnlockCountdownEnabled = availability.countdownEnabled,
                    emergencyUnlockCountdownSeconds = availability.countdownSeconds,
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

        internal fun trackEmergencyUnlockStepViewed(stepName: String) {
            analytics.trackEmergencyUnlockStepViewed(
                stepName = stepName,
                reasonRequiredEnabled = container.stateFlow.value.emergencyUnlockReasonRequired,
                source = AnalyticsSource.LOCK_SCREEN,
            )
        }

        internal fun trackEmergencyUnlockValidationBlocked(
            stepName: String,
            validationReason: String,
        ) {
            analytics.trackEmergencyUnlockValidationBlocked(
                stepName = stepName,
                validationReason = validationReason,
                reasonRequiredEnabled = container.stateFlow.value.emergencyUnlockReasonRequired,
                source = AnalyticsSource.LOCK_SCREEN,
            )
        }

        internal fun trackEmergencyUnlockCancelled(stepName: String) {
            analytics.trackEmergencyUnlockCancelled(
                stepName = stepName,
                reasonRequiredEnabled = container.stateFlow.value.emergencyUnlockReasonRequired,
                source = AnalyticsSource.LOCK_SCREEN,
                cancelSource = AnalyticsEmergencyUnlockCancelSource.CANCEL_BUTTON,
            )
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
    val selectedWebDomains: Set<String> = emptySet(),
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
    val emergencyUnlockCountdownEnabled: Boolean = DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_ENABLED,
    val emergencyUnlockCountdownSeconds: Int = DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_SECONDS,
    val emergencyUnlockAvailabilityReason: EmergencyUnlockAvailabilityReason = EmergencyUnlockAvailabilityReason.Available,
    val isEmergencyUnlockActive: Boolean = false,
    val emergencyUnlockRemainingSeconds: Int = 0,
    val emergencyUnlockedApps: Set<String> = emptySet(),
)

sealed class LockSideEffect {
    data object MoveToHome : LockSideEffect()
    data object UnlockCompleted : LockSideEffect()
}

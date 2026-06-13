package com.uiery.keep.feature.home

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.AnalyticsEndReason
import com.uiery.keep.analytics.AnalyticsRoutineCreationCtaActivationStage
import com.uiery.keep.analytics.AnalyticsRoutineCreationCtaSurface
import com.uiery.keep.analytics.AnalyticsRoutineCreationCtaVariant
import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionAnalyticsPayload
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionSurface
import com.uiery.keep.analytics.routine.RoutineSavedCreationSource
import com.uiery.keep.analytics.RoutineCountAnalyticsSync
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.datastore.ReviewPromptStateStore
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockPolicy
import com.uiery.keep.data.goallock.GoalLockRepository
import com.uiery.keep.domain.goallock.GoalLockRuntimeStatus
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import com.uiery.keep.feature.goallock.analyticsLockMode
import com.uiery.keep.feature.goallock.goalLockDurationDaysBucket
import com.uiery.keep.feature.lockhistory.LockHistoryRepository
import com.uiery.keep.feature.routine.RepeatBlockHistorySample
import com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestion
import com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestionPolicy
import com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestionStore
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.feature.review.InAppReviewManager
import com.uiery.keep.feature.review.ReviewEligibilityDecision
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.feature.review.SkipReason
import com.uiery.keep.service.LockHistoryRecorder
import com.uiery.keep.util.timeNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalTime
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @KeepDataSource private val dataStore: DataStore<Preferences>,
        private val blockingStateStore: BlockingStateStore,
        private val reviewPromptStateStore: ReviewPromptStateStore,
        private val routineNoticeStore: RoutineNoticeStore,
        private val analytics: KeepAnalytics,
        private val routineDao: RoutineDao,
        private val routineCountAnalyticsSync: RoutineCountAnalyticsSync,
        private val lockHistoryRecorder: LockHistoryRecorder,
        private val goalLockRepository: GoalLockRepository,
        private val lockHistoryRepository: LockHistoryRepository,
        private val routineRepository: RoutineRepository,
        private val repeatBlockSuggestionStore: RepeatBlockRoutineSuggestionStore,
        private val reviewEligibility: ReviewEligibilityEvaluator,
        private val inAppReviewManager: InAppReviewManager,
    ) : ViewModel(),
        ContainerHost<HomeUiState, HomeSideEffect> {
        override val container: Container<HomeUiState, HomeSideEffect> = container(HomeUiState())

        init {
            getIsKeep()
            getSelectedApp()
            getRoutineCreationCta()
            syncRoutinesCount()
            getGoalLockCard()
            loadRepeatBlockRoutineSuggestion()
        }

        internal fun changeIsKeep(
            noSelectedAppsMessage: String? = null,
            firstLockStartedMessage: String? = null,
        ) =
            intent {
                val isKeep = !state.isKeep
                if (isKeep && state.selectedAppPackage.isEmpty()) {
                    reduce {
                        state.copy(
                            isShowCategoryBottomSheet = true,
                            sheetVisible = true,
                        )
                    }
                    if (!noSelectedAppsMessage.isNullOrBlank()) {
                        postSideEffect(HomeSideEffect.ShowSnackBar(noSelectedAppsMessage))
                        reduce { state.copy(snackbarMessage = noSelectedAppsMessage) }
                    }
                    return@intent
                }
                analytics.trackKeepModeToggled(isEnabled = isKeep)
                if (isKeep) {
                    if (trackFirstLockConfiguredIfNeeded(source = AnalyticsSource.HOME)) {
                        if (!firstLockStartedMessage.isNullOrBlank()) {
                            postSideEffect(HomeSideEffect.ShowSnackBar(firstLockStartedMessage))
                            reduce {
                                state.copy(
                                    showFirstLockActivationCta = false,
                                    snackbarMessage = firstLockStartedMessage,
                                )
                            }
                        } else {
                            reduce { state.copy(showFirstLockActivationCta = false) }
                        }
                    }
                    analytics.trackLockSessionStart(
                        source = AnalyticsSource.HOME_KEEP_SWITCH,
                        isRoutine = false,
                    )
                    storeStartTime()
                } else {
                    analytics.trackLockSessionEnd(
                        source = AnalyticsSource.HOME_KEEP_SWITCH,
                        endReason = AnalyticsEndReason.USER_TOGGLE_OFF,
                        isRoutine = false,
                    )
                    storeBlockTime(System.currentTimeMillis() - state.startTime)
                }
                reduce {
                    state.copy(
                        isKeep = isKeep,
                        startTime = System.currentTimeMillis(),
                        showRoutineCreationCta = false,
                    )
                }
                storeIsKeep()
            }

        internal fun showSnackBar(message: String) =
            intent {
                postSideEffect(HomeSideEffect.ShowSnackBar(message))
                CoroutineScope(Dispatchers.IO).launch {
                    reduce { state.copy(snackbarMessage = message) }
                }
            }

        internal fun showCategoryBottomSheet() =
            intent {
                // 잠금 활성 중에는 차단 앱 선택을 변경할 수 없다 (우회 방지).
                if (state.isKeep) return@intent
                reduce {
                    state.copy(
                        isShowCategoryBottomSheet = true,
                        sheetVisible = true,
                    )
                }
            }

        internal fun showTimeBottomSheet() =
            intent {
                reduce {
                    state.copy(
                        isShowTimeBottomSheet = true,
                        sheetVisible = true,
                    )
                }
            }

        internal fun hideCategoryBottomSheet() =
            intent {
                reduce {
                    state.copy(
                        isShowCategoryBottomSheet = false,
                        sheetVisible = state.isShowTimeBottomSheet,
                    )
                }
                val pendingMessage = takePendingRoutineStartNoticeIfReady(sheetVisible = state.sheetVisible)
                if (!pendingMessage.isNullOrBlank()) {
                    postSideEffect(HomeSideEffect.ShowSnackBar(pendingMessage))
                    reduce { state.copy(snackbarMessage = pendingMessage) }
                }
            }

        internal fun hideTimeBottomSheet() =
            intent {
                reduce {
                    state.copy(
                        isShowTimeBottomSheet = false,
                        sheetVisible = state.isShowCategoryBottomSheet,
                    )
                }
                val pendingMessage = takePendingRoutineStartNoticeIfReady(sheetVisible = state.sheetVisible)
                if (!pendingMessage.isNullOrBlank()) {
                    postSideEffect(HomeSideEffect.ShowSnackBar(pendingMessage))
                    reduce { state.copy(snackbarMessage = pendingMessage) }
                }
            }

        internal fun maybeDrainReviewFlag(activity: Activity?) =
            intent {
                if (!reviewPromptStateStore.readState().isPending) return@intent
                if (state.sheetVisible) {
                    analytics.reviewPromptSkipped(SkipReason.NotHomeRoot.name)
                    return@intent
                }
                val live = reviewEligibility.evaluateLive()
                if (live is ReviewEligibilityDecision.Ineligible) {
                    analytics.reviewPromptSkipped(live.reason.name)
                    reviewPromptStateStore.clearPending()
                    return@intent
                }
                if (activity == null) {
                    analytics.reviewPromptSkipped(SkipReason.NoActivity.name)
                    return@intent
                }
                val launched = inAppReviewManager.launchIfReady(activity)
                if (launched) {
                    reviewPromptStateStore.clearPending()
                }
            }

        internal fun maybeDrainRoutineStartNotice() =
            intent {
                val pendingMessage = takePendingRoutineStartNoticeIfReady(sheetVisible = state.sheetVisible)
                if (pendingMessage.isNullOrBlank()) return@intent

                postSideEffect(HomeSideEffect.ShowSnackBar(pendingMessage))
                reduce { state.copy(snackbarMessage = pendingMessage) }
            }

        private suspend fun takePendingRoutineStartNoticeIfReady(sheetVisible: Boolean): String? {
            if (sheetVisible) return null
            return routineNoticeStore.drainNextPendingRoutineStartNotice()
        }

        internal fun dismissRepeatBlockRoutineSuggestion() =
            intent {
                val suggestion = state.repeatBlockRoutineSuggestion ?: return@intent
                repeatBlockSuggestionStore.recordDismissed(
                    suggestion = suggestion,
                    dismissedAt = LocalDateTime.now(),
                )
                analytics.trackRepeatBlockRoutineSuggestionDismissed(
                    surface = RepeatBlockRoutineSuggestionSurface.HOME,
                    suggestion = suggestion.toAnalyticsPayload(),
                )
                reduce { state.copy(repeatBlockRoutineSuggestion = null) }
            }

        internal fun openRepeatBlockRoutineSuggestion() =
            intent {
                val suggestion = state.repeatBlockRoutineSuggestion ?: return@intent
                analytics.trackRepeatBlockRoutineSuggestionClicked(
                    surface = RepeatBlockRoutineSuggestionSurface.HOME,
                    suggestion = suggestion.toAnalyticsPayload(),
                )
                postSideEffect(HomeSideEffect.NavigateToRoutineWithRepeatBlockPrefill(suggestion))
            }

        private fun loadRepeatBlockRoutineSuggestion() =
            intent {
                val now = LocalDateTime.now()
                val startMillis = now.minusDays(14)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val endMillis = now.plusDays(1)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val histories = lockHistoryRepository.sessionsInRange(startMillis, endMillis)
                    .firstOrNull()
                    .orEmpty()
                    .map { history ->
                        RepeatBlockHistorySample(
                            startDateTime = history.startDateTime,
                            blockedPackages = history.lockedApps,
                        )
                    }
                val routines = routineRepository.fetchAll().firstOrNull().orEmpty()
                val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
                    histories = histories,
                    activeRoutines = routines,
                    dismissedSuggestions = repeatBlockSuggestionStore.readDismissedSuggestions(),
                    now = now,
                )

                reduce { state.copy(repeatBlockRoutineSuggestion = suggestion) }
                if (suggestion != null) {
                    analytics.trackRepeatBlockRoutineSuggestionShown(
                        surface = RepeatBlockRoutineSuggestionSurface.HOME,
                        suggestion = suggestion.toAnalyticsPayload(),
                    )
                }
            }

        internal fun moveToLock() =
            intent {
                val routeDeadline = state.pendingManualLockRouteDeadline ?: run {
                    val targetDateTime = if (state.manualLockMode == ManualLockMode.COUNTDOWN) {
                        calculateCountdownTargetDateTime(state.countdownDays, state.countdownTime)
                    } else {
                        calculateTargetLockDateTime(state.blockTime)
                    }
                    val targetInstant = targetDateTime.atZone(ZoneId.systemDefault()).toInstant()
                    ManualLockTimePolicy.encodeDeadline(targetInstant)
                }
                postSideEffect(HomeSideEffect.MoveToLock(routeDeadline, false))
                reduce { state.copy(pendingManualLockRouteDeadline = null) }
            }

        internal fun onRoutineCreationCtaClick() =
            intent {
                if (!state.showRoutineCreationCta) return@intent

                analytics.trackRoutineCreationCtaClicked(
                    surface = AnalyticsRoutineCreationCtaSurface.HOME_SECONDARY,
                    activationStage = AnalyticsRoutineCreationCtaActivationStage.POST_FIRST_CORE_ACTION,
                    hasRoutine = state.routineCount > 0,
                    ctaVariant = AnalyticsRoutineCreationCtaVariant.SOFT_DEFAULT,
                )
                postSideEffect(
                    HomeSideEffect.MoveToRoutine(
                        routineSavedEntrySurface = AnalyticsRoutineCreationCtaSurface.HOME_SECONDARY,
                        routineSavedCreationSource = RoutineSavedCreationSource.POST_FIRST_BLOCK_CTA,
                    ),
                )
            }

        private fun getSelectedApp() =
            intent {
                val selectionState = blockingStateStore.readSelectionState()
                val firstCoreActionState = blockingStateStore.readFirstCoreActionState(
                    fallbackFirstOpenTimestampMillis = System.currentTimeMillis(),
                )
                val showRoutineCreationCta = shouldShowRoutineCreationCta(
                    selectedAppPackage = selectionState.selectedAppPackages,
                    hasTrackedFirstCoreAction = firstCoreActionState.hasTrackedFirstCoreAction,
                    routineCount = state.routineCount,
                    isKeep = state.isKeep,
                )
                trackRoutineCreationCtaShownIfNeeded(
                    shouldShow = showRoutineCreationCta,
                    wasShowing = state.showRoutineCreationCta,
                    hasRoutine = state.routineCount > 0,
                )
                reduce {
                    state.copy(
                        selectedAppPackage = selectionState.selectedAppPackages,
                        showFirstLockActivationCta = shouldShowFirstLockActivationCta(
                            selectedAppPackage = selectionState.selectedAppPackages,
                            hasTrackedFirstLock = selectionState.hasTrackedFirstLockConfigured,
                            isKeep = state.isKeep,
                        ),
                        showRoutineCreationCta = showRoutineCreationCta,
                    )
                }
            }

        private fun getRoutineCreationCta() =
            intent {
                routineDao.fetchAll().collect { routines ->
                    val selectionState = blockingStateStore.readSelectionState()
                    val firstCoreActionState = blockingStateStore.readFirstCoreActionState(
                        fallbackFirstOpenTimestampMillis = System.currentTimeMillis(),
                    )
                    val showRoutineCreationCta = shouldShowRoutineCreationCta(
                        selectedAppPackage = selectionState.selectedAppPackages,
                        hasTrackedFirstCoreAction = firstCoreActionState.hasTrackedFirstCoreAction,
                        routineCount = routines.size,
                        isKeep = state.isKeep,
                    )
                    trackRoutineCreationCtaShownIfNeeded(
                        shouldShow = showRoutineCreationCta,
                        wasShowing = state.showRoutineCreationCta,
                        hasRoutine = routines.isNotEmpty(),
                    )
                    reduce {
                        state.copy(
                            routineCount = routines.size,
                            showRoutineCreationCta = showRoutineCreationCta,
                        )
                    }
                }
            }

        private fun syncRoutinesCount() =
            intent {
                routineCountAnalyticsSync.syncFromRoom()
            }

        private fun getGoalLockCard() =
            intent {
                goalLockRepository.fetchAll().collect { goalLocks ->
                    val today = LocalDate.now()
                    val card = selectGoalLockForHomeCard(goalLocks, today)
                        ?.toHomeGoalLockCardState(today)
                    reduce { state.copy(goalLockCard = card) }
                }
            }

        private fun selectGoalLockForHomeCard(
            goalLocks: List<GoalLock>,
            today: LocalDate,
        ): HomeGoalLockCardCandidate? = goalLocks
            .map { goalLock ->
                val normalizedGoalLock = completeExpiredGoalLockIfNeeded(goalLock, today)
                HomeGoalLockCardCandidate(
                    goalLock = normalizedGoalLock,
                    runtimeStatus = GoalLockPolicy.runtimeStatus(normalizedGoalLock, today.atStartOfDay()),
                )
            }
            .minWithOrNull(
                compareBy<HomeGoalLockCardCandidate> { it.runtimeStatus.homeCardPriority }
                    .thenBy { it.homeCardSecondarySortKey }
                    .thenBy { it.goalLock.id },
            )

        private fun HomeGoalLockCardCandidate.toHomeGoalLockCardState(today: LocalDate): HomeGoalLockCardState =
            HomeGoalLockCardState(
                goalLockId = goalLock.id,
                goalName = goalLock.goalName,
                status = runtimeStatus.toHomeStatus(),
                daysRemaining = ChronoUnit.DAYS.between(today, goalLock.endDate).toInt().plus(1).coerceAtLeast(0),
                lockModeLabel = goalLock.lockMode.homeLabel,
                selectedAppCount = goalLock.selectedPackages.size,
            )

        private fun completeExpiredGoalLockIfNeeded(
            goalLock: GoalLock,
            today: LocalDate,
        ): GoalLock {
            if (goalLock.status != GoalLockStoredStatus.Active) return goalLock
            if (GoalLockPolicy.runtimeStatus(goalLock, today.atStartOfDay()) != GoalLockRuntimeStatus.Completed) return goalLock

            val completed = goalLock.copy(status = GoalLockStoredStatus.Completed)
            goalLockRepository.update(completed)
            analytics.trackGoalLockCompleted(
                lockMode = goalLock.lockMode.analyticsLockMode,
                durationDaysBucket = goalLockDurationDaysBucket(goalLock.startDate, goalLock.endDate),
            )
            return completed
        }

        private fun storeSelectedApp(selectedAppPackage: Set<String>) =
            intent {
                blockingStateStore.saveSelectedAppPackages(selectedAppPackage)
            }

        private fun storeBlockTime(
            lockedMillis: Long,
            isRoutine: Boolean = false,
        ) = intent {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - lockedMillis
            lockHistoryRecorder.recordSession(
                startTimestamp = startTime,
                endTimestamp = endTime,
                lockedApps = state.selectedAppPackage,
                isRoutine = isRoutine,
            )
        }

        internal fun selectCategoryComplete(selectedAppPackage: Set<String>) =
            intent {
                analytics.trackAppSelectionCompleted(
                    selectedAppCount = selectedAppPackage.size,
                    isOnboarding = false,
                )
                storeSelectedApp(selectedAppPackage)
                val hasTrackedFirstLock = blockingStateStore.readSelectionState().hasTrackedFirstLockConfigured
                val firstCoreActionState = blockingStateStore.readFirstCoreActionState(
                    fallbackFirstOpenTimestampMillis = System.currentTimeMillis(),
                )
                val showRoutineCreationCta = shouldShowRoutineCreationCta(
                    selectedAppPackage = selectedAppPackage,
                    hasTrackedFirstCoreAction = firstCoreActionState.hasTrackedFirstCoreAction,
                    routineCount = state.routineCount,
                    isKeep = state.isKeep,
                )
                trackRoutineCreationCtaShownIfNeeded(
                    shouldShow = showRoutineCreationCta,
                    wasShowing = state.showRoutineCreationCta,
                    hasRoutine = state.routineCount > 0,
                )
                reduce {
                    state.copy(
                        selectedAppPackage = selectedAppPackage,
                        showFirstLockActivationCta = shouldShowFirstLockActivationCta(
                            selectedAppPackage = selectedAppPackage,
                            hasTrackedFirstLock = hasTrackedFirstLock,
                            isKeep = state.isKeep,
                        ),
                        showRoutineCreationCta = showRoutineCreationCta,
                    )
                }
            }

        private fun storeIsKeep() =
            intent {
                blockingStateStore.setIsKeep(state.isKeep)
            }

        private fun storeStartTime() =
            intent {
                blockingStateStore.saveStartTime(System.currentTimeMillis())
            }

        private fun getStartTime() =
            intent {
                val startTime = blockingStateStore.readStartTime()
                reduce { state.copy(startTime = startTime ?: System.currentTimeMillis()) }
            }

        private fun getIsKeep() =
            intent {
                val isKeep = blockingStateStore.readIsKeep()
                reduce { state.copy(isKeep = isKeep) }
                if (isKeep) {
                    getStartTime()
                }
            }

        internal fun updateCountdownDuration(duration: CountdownDuration) =
            intent {
                val blockTime =
                    timeNow
                        .toJavaLocalTime()
                        .plusHours(duration.hour.toLong())
                        .plusMinutes(duration.minute.toLong())
                        .toKotlinLocalTime()
                reduce {
                    state.copy(
                        manualLockMode = ManualLockMode.COUNTDOWN,
                        countdownTime = LocalTime(duration.hour, duration.minute),
                        countdownDays = duration.day,
                        blockTime = blockTime,
                    )
                }
            }

        internal fun updateTimerTime(timerTime: LocalTime) =
            intent {
                reduce {
                    state.copy(
                        manualLockMode = ManualLockMode.TIMER,
                        timerTime = timerTime,
                        blockTime = timerTime,
                    )
                }
            }

        internal fun updateManualLockMode(mode: ManualLockMode) =
            intent {
                reduce {
                    state.copy(
                        manualLockMode = mode,
                        blockTime = when (mode) {
                            ManualLockMode.COUNTDOWN -> timeNow
                                .toJavaLocalTime()
                                .plusHours(state.countdownTime.hour.toLong())
                                .plusMinutes(state.countdownTime.minute.toLong())
                                .toKotlinLocalTime()
                            ManualLockMode.TIMER -> state.timerTime
                        },
                    )
                }
            }

        internal fun lockTime(
            noSelectedAppsMessage: String? = null,
            firstLockScheduledMessage: String? = null,
        ) =
            intent {
                if (state.selectedAppPackage.isEmpty()) {
                    reduce {
                        state.copy(
                            isShowCategoryBottomSheet = true,
                            sheetVisible = true,
                        )
                    }
                    if (!noSelectedAppsMessage.isNullOrBlank()) {
                        postSideEffect(HomeSideEffect.ShowSnackBar(noSelectedAppsMessage))
                        reduce { state.copy(snackbarMessage = noSelectedAppsMessage) }
                    }
                    return@intent
                }
                if (state.manualLockMode == ManualLockMode.COUNTDOWN && state.countdownDurationIsZero()) {
                    return@intent
                }
                val sessionStartTime = System.currentTimeMillis()
                val targetLockDateTime = if (state.manualLockMode == ManualLockMode.COUNTDOWN) {
                    calculateCountdownTargetDateTime(state.countdownDays, state.countdownTime)
                } else {
                    calculateTargetLockDateTime(state.blockTime)
                }
                val targetLockInstant = targetLockDateTime.atZone(ZoneId.systemDefault()).toInstant()
                val encodedDeadline = ManualLockTimePolicy.encodeDeadline(targetLockInstant)
                blockingStateStore.saveLockTime(encodedDeadline)
                blockingStateStore.saveStartTime(sessionStartTime)
                reduce { state.copy(pendingManualLockRouteDeadline = encodedDeadline) }
                val lockedDurationMinutes = if (state.manualLockMode == ManualLockMode.COUNTDOWN) {
                    state.countdownDurationMinutes()
                } else {
                    Duration
                        .between(java.time.Instant.now(), targetLockInstant)
                        .toMillis()
                        .coerceAtLeast(0L) / 60_000L
                }
                if (trackFirstLockConfiguredIfNeeded(source = AnalyticsSource.HOME_TIMER)) {
                    if (!firstLockScheduledMessage.isNullOrBlank()) {
                        postSideEffect(HomeSideEffect.ShowSnackBar(firstLockScheduledMessage))
                        reduce {
                            state.copy(
                                showFirstLockActivationCta = false,
                                snackbarMessage = firstLockScheduledMessage,
                            )
                        }
                    } else {
                        reduce { state.copy(showFirstLockActivationCta = false) }
                    }
                }
                analytics.trackLockScheduled(
                    scheduleType = if (state.manualLockMode == ManualLockMode.COUNTDOWN) {
                        AnalyticsScheduleType.COUNTDOWN
                    } else {
                        AnalyticsScheduleType.TIMER
                    },
                    scheduledDurationMinutes = lockedDurationMinutes,
                )
                analytics.trackLockSessionStart(
                    source = AnalyticsSource.HOME_TIMER,
                    isRoutine = false,
                )
            }

        private suspend fun trackFirstLockConfiguredIfNeeded(source: String): Boolean {
            if (!blockingStateStore.markFirstLockConfiguredIfNeeded()) return false

            val selectedAppCount = blockingStateStore.readSelectedAppPackages().size

            analytics.trackFirstLockConfigured(
                source = source,
                selectedAppCount = selectedAppCount,
            )
            return true
        }

        private fun shouldShowFirstLockActivationCta(
            selectedAppPackage: Set<String>,
            hasTrackedFirstLock: Boolean,
            isKeep: Boolean,
        ): Boolean = selectedAppPackage.isNotEmpty() && !hasTrackedFirstLock && !isKeep

        private fun shouldShowRoutineCreationCta(
            selectedAppPackage: Set<String>,
            hasTrackedFirstCoreAction: Boolean,
            routineCount: Int,
            isKeep: Boolean,
        ): Boolean = selectedAppPackage.isNotEmpty() && hasTrackedFirstCoreAction && routineCount == 0 && !isKeep

        private fun trackRoutineCreationCtaShownIfNeeded(
            shouldShow: Boolean,
            wasShowing: Boolean,
            hasRoutine: Boolean,
        ) {
            if (!shouldShow || wasShowing) return

            analytics.trackRoutineCreationCtaShown(
                surface = AnalyticsRoutineCreationCtaSurface.HOME_SECONDARY,
                activationStage = AnalyticsRoutineCreationCtaActivationStage.POST_FIRST_CORE_ACTION,
                hasRoutine = hasRoutine,
                ctaVariant = AnalyticsRoutineCreationCtaVariant.SOFT_DEFAULT,
            )
        }

        private fun calculateTargetLockDateTime(blockTime: LocalTime): LocalDateTime {
            val nowDateTime = LocalDateTime.now()
            val target =
                nowDateTime
                    .withHour(blockTime.hour)
                    .withMinute(blockTime.minute)
                    .withSecond(0)
                    .withNano(0)

            return if (target.isBefore(nowDateTime)) target.plusDays(1) else target
        }

        private fun calculateCountdownTargetDateTime(days: Int, countdownTime: LocalTime): LocalDateTime =
            LocalDateTime.now()
                .plusDays(days.toLong())
                .plusHours(countdownTime.hour.toLong())
                .plusMinutes(countdownTime.minute.toLong())

        internal fun analyticsHomeScreen() =
            intent {
                analytics.logScreenView(KeepAnalyticsScreen.HOME)
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
    val isSelectAll: Boolean = true,
    val blockTime: LocalTime = timeNow,
    val countdownTime: LocalTime = LocalTime(0, 0),
    val timerTime: LocalTime = timeNow,
    val manualLockMode: ManualLockMode = ManualLockMode.COUNTDOWN,
    val countdownDays: Int = 0,
    val sheetVisible: Boolean = false,
    val showFirstLockActivationCta: Boolean = false,
    val showRoutineCreationCta: Boolean = false,
    val routineCount: Int = 0,
    val pendingManualLockRouteDeadline: String? = null,
    val goalLockCard: HomeGoalLockCardState? = null,
    val repeatBlockRoutineSuggestion: RepeatBlockRoutineSuggestion? = null,
) {
    fun countdownDurationIsZero(): Boolean =
        countdownDurationMinutes() == 0L

    fun countdownDurationMinutes(): Long =
        countdownDays * 24L * 60L + countdownTime.hour * 60L + countdownTime.minute
}

private data class HomeGoalLockCardCandidate(
    val goalLock: GoalLock,
    val runtimeStatus: GoalLockRuntimeStatus,
)

private val GoalLockRuntimeStatus.homeCardPriority: Int
    get() = when (this) {
        GoalLockRuntimeStatus.Active -> 0
        GoalLockRuntimeStatus.Pending -> 1
        GoalLockRuntimeStatus.Completed -> 2
        GoalLockRuntimeStatus.EndedEarly -> 3
    }

private val HomeGoalLockCardCandidate.homeCardSecondarySortKey: Long
    get() = when (runtimeStatus) {
        GoalLockRuntimeStatus.Active -> goalLock.endDate.toEpochDay()
        GoalLockRuntimeStatus.Pending -> goalLock.startDate.toEpochDay()
        GoalLockRuntimeStatus.Completed,
        GoalLockRuntimeStatus.EndedEarly,
        -> -goalLock.endDate.toEpochDay()
    }

data class HomeGoalLockCardState(
    val goalLockId: Long,
    val goalName: String,
    val status: HomeGoalLockStatus,
    val daysRemaining: Int,
    val lockModeLabel: String,
    val selectedAppCount: Int,
)

enum class HomeGoalLockStatus {
    Pending,
    Active,
    Completed,
    EndedEarly,
}

private fun GoalLockRuntimeStatus.toHomeStatus(): HomeGoalLockStatus = when (this) {
    GoalLockRuntimeStatus.Pending -> HomeGoalLockStatus.Pending
    GoalLockRuntimeStatus.Active -> HomeGoalLockStatus.Active
    GoalLockRuntimeStatus.EndedEarly -> HomeGoalLockStatus.EndedEarly
    GoalLockRuntimeStatus.Completed -> HomeGoalLockStatus.Completed
}

private val GoalLockMode.homeLabel: String
    get() = when (this) {
        GoalLockMode.AllDay -> "하루종일 잠금"
        is GoalLockMode.Scheduled -> "특정 시간 잠금"
    }

private fun RepeatBlockRoutineSuggestion.toAnalyticsPayload() = RepeatBlockRoutineSuggestionAnalyticsPayload(
    reason = reason.analyticsValue,
    timeBucket = timeBucket.analyticsValue,
    dayType = dayType.analyticsValue,
    categoryBucket = categoryBucket.analyticsValue,
    repeatCountBucket = repeatCountBucket.analyticsValue,
    routineCoverageState = routineCoverageState.analyticsValue,
)

data class CountdownDuration(val day: Int = 0, val hour: Int = 0, val minute: Int = 0)

enum class ManualLockMode {
    COUNTDOWN,
    TIMER,
}

sealed class HomeSideEffect {
    data class ShowSnackBar(
        val message: String,
    ) : HomeSideEffect()

    data class MoveToLock(
        val lockTime: String?,
        val isRoutine: Boolean,
    ) : HomeSideEffect()

    data class MoveToRoutine(
        val routineSavedEntrySurface: String? = null,
        val routineSavedCreationSource: String? = null,
    ) : HomeSideEffect()

    data class NavigateToRoutineWithRepeatBlockPrefill(
        val suggestion: RepeatBlockRoutineSuggestion,
    ) : HomeSideEffect()
}

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
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.FirstLockConfiguredDeliveryCoordinator
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionAnalyticsPayload
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionSurface
import com.uiery.keep.analytics.routine.RoutineSavedCreationSource
import com.uiery.keep.analytics.RoutineCountAnalyticsSync
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
import com.uiery.keep.data.lockhistory.LockHistoryRepository
import com.uiery.keep.data.lock.TimedLockStartOrigin
import com.uiery.keep.data.lock.TimedLockStartResult
import com.uiery.keep.data.lock.TimedLockStarter
import com.uiery.keep.data.lock.TimedLockHomeScheduleType
import com.uiery.keep.domain.repeatblock.RepeatBlockHistorySample
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestionPolicy
import com.uiery.keep.data.repeatblock.RepeatBlockRoutineSuggestionStore
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.data.usageinsight.UsageInsightCardResult
import com.uiery.keep.data.usageinsight.UsageInsightRepository
import com.uiery.keep.domain.usageinsight.UsageInsightRoutinePrefill
import com.uiery.keep.domain.usageinsight.toRoutinePrefill
import com.uiery.keep.domain.lock.LockTargetKind
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.domain.websiteblocking.DomainName
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingPolicy
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingSession
import com.uiery.keep.domain.websiteblocking.toRoutineWebsiteWindows
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingRuntimeDecision
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingRuntimePolicy
import com.uiery.keep.domain.websiteblocking.WebsiteLockRecommendation
import com.uiery.keep.domain.websiteblocking.WebsiteLockRecommendationPolicy
import com.uiery.keep.feature.home.component.UsageInsightCardUiState
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
        private val routineCountAnalyticsSync: RoutineCountAnalyticsSync,
        private val lockHistoryRecorder: LockHistoryRecorder,
        private val goalLockRepository: GoalLockRepository,
        private val lockHistoryRepository: LockHistoryRepository,
        private val routineRepository: RoutineRepository,
        private val repeatBlockSuggestionStore: RepeatBlockRoutineSuggestionStore,
        private val usageInsightRepository: UsageInsightRepository,
        private val reviewEligibility: ReviewEligibilityEvaluator,
        private val inAppReviewManager: InAppReviewManager,
        private val timedLockStarter: TimedLockStarter,
        private val firstPromiseRecovery: FirstPromiseHomeRecovery = NoOpFirstPromiseHomeRecovery,
        private val firstLockDelivery: FirstLockConfiguredDeliveryCoordinator =
            FirstLockConfiguredDeliveryCoordinator(blockingStateStore, analytics),
    ) : ViewModel(),
        ContainerHost<HomeUiState, HomeSideEffect> {
        override val container: Container<HomeUiState, HomeSideEffect> = container(HomeUiState())

        // 마지막으로 usage_insight_card_shown 을 기록한 카드 식별자. 홈 복귀 재평가 시 동일 카드 중복 로깅을 막는다.
        // key = "permission_needed" 또는 인사이트 type.analyticsValue. dismiss 시 null 로 초기화한다.
        private var lastShownCardKey: String? = null

        init {
            getIsKeep()
            getActiveTimedLock()
            getSelectedApp()
            getRoutineCreationCta()
            syncRoutinesCount()
            getGoalLockCard()
            loadRepeatBlockRoutineSuggestion()
            loadUsageInsightCard()
            loadFirstPromiseResumeCard()
        }

        internal fun loadFirstPromiseResumeCard() =
            intent {
                when (val decision = firstPromiseRecovery.load()) {
                    is FirstPromiseResumeDecision.Show ->
                        reduce { state.copy(firstPromiseResumeCard = decision.card) }
                    FirstPromiseResumeDecision.Hidden ->
                        reduce { state.copy(firstPromiseResumeCard = null) }
                    FirstPromiseResumeDecision.OpenSettings ->
                        postSideEffect(HomeSideEffect.OpenExactAlarmSettings)
                }
            }

        internal fun activateFirstPromiseResumeCard() =
            intent {
                val card = state.firstPromiseResumeCard ?: return@intent
                if (card.isBusy) return@intent
                reduce {
                    state.copy(firstPromiseResumeCard = card.copy(isBusy = true))
                }
                when (val decision = firstPromiseRecovery.activate()) {
                    is FirstPromiseResumeDecision.Show ->
                        reduce { state.copy(firstPromiseResumeCard = decision.card) }
                    FirstPromiseResumeDecision.Hidden ->
                        reduce { state.copy(firstPromiseResumeCard = null) }
                    FirstPromiseResumeDecision.OpenSettings ->
                        postSideEffect(HomeSideEffect.OpenExactAlarmSettings)
                }
            }

        internal fun onFirstPromiseExactAlarmSettingsUnavailable() =
            intent {
                when (val decision = firstPromiseRecovery.onSettingsLaunchFailed()) {
                    is FirstPromiseResumeDecision.Show ->
                        reduce { state.copy(firstPromiseResumeCard = decision.card) }
                    FirstPromiseResumeDecision.Hidden ->
                        reduce { state.copy(firstPromiseResumeCard = null) }
                    FirstPromiseResumeDecision.OpenSettings -> Unit
                }
            }

        internal fun onFirstPromiseExactAlarmResume() =
            intent {
                when (val decision = firstPromiseRecovery.onResume()) {
                    is FirstPromiseResumeDecision.Show ->
                        reduce { state.copy(firstPromiseResumeCard = decision.card) }
                    FirstPromiseResumeDecision.Hidden ->
                        reduce { state.copy(firstPromiseResumeCard = null) }
                    FirstPromiseResumeDecision.OpenSettings ->
                        postSideEffect(HomeSideEffect.OpenExactAlarmSettings)
                }
            }

        internal fun changeIsKeep(
            firstLockStartedMessage: String? = null,
        ) =
            intent {
                val isKeep = !state.isKeep
                if (isKeep && !state.hasSelectedLockTargets()) {
                    // 차단 대상이 없는 것은 오류가 아니라 시작 과정의 한 단계다. 선택 시트를 바로
                    // 열어 다음 단계로 잇는다. 시트 위에 스낵바를 겹쳐 띄우면 시트를 가리고
                    // 사용자를 나무라는 인상만 남는다.
                    reduce {
                        state.copy(
                            isShowCategoryBottomSheet = true,
                            sheetVisible = true,
                        )
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

        internal fun trackWebsiteBlockingConsentResult(granted: Boolean) {
            analytics.trackWebsiteBlockingConsentResult(
                granted = granted,
                source = AnalyticsSource.HOME,
            )
        }

        internal fun trackWebsiteBlockingVpnConflictResolved(displacedOtherVpn: Boolean) {
            analytics.trackWebsiteBlockingVpnConflictResolved(
                displacedOtherVpn = displacedOtherVpn,
                source = AnalyticsSource.HOME,
            )
        }

        internal fun showCategoryBottomSheet() =
            intent {
                // 잠금 활성 중에는 차단 앱 선택을 변경할 수 없다 (우회 방지).
                if (state.isKeep || activeTimedLockExists()) return@intent
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
                    postSideEffect(
                        HomeSideEffect.ShowSnackBar(
                            message = pendingMessage,
                            drainNextRoutineStartNoticeAfterDismiss = true,
                        ),
                    )
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
                    postSideEffect(
                        HomeSideEffect.ShowSnackBar(
                            message = pendingMessage,
                            drainNextRoutineStartNoticeAfterDismiss = true,
                        ),
                    )
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

                postSideEffect(
                    HomeSideEffect.ShowSnackBar(
                        message = pendingMessage,
                        drainNextRoutineStartNoticeAfterDismiss = true,
                    ),
                )
                reduce { state.copy(snackbarMessage = pendingMessage) }
            }

        internal fun onRoutineStartNoticeSnackbarFinished() =
            intent {
                val pendingMessage = takePendingRoutineStartNoticeIfReady(sheetVisible = state.sheetVisible)
                if (pendingMessage.isNullOrBlank()) return@intent

                postSideEffect(
                    HomeSideEffect.ShowSnackBar(
                        message = pendingMessage,
                        drainNextRoutineStartNoticeAfterDismiss = true,
                    ),
                )
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
                val hasActiveGoalLock = goalLockRepository.fetchAll()
                    .firstOrNull()
                    .orEmpty()
                    .any { goalLock ->
                        GoalLockPolicy.isCurrentlyProtecting(goalLock, now)
                    }
                if (hasActiveGoalLock) {
                    reduce { state.copy(repeatBlockRoutineSuggestion = null) }
                    return@intent
                }

                val hasActiveEmergencyUnlock = blockingStateStore.accessibilitySnapshot
                    .firstOrNull()
                    ?.let { snapshot ->
                        snapshot.emergencyUnlockApps.isNotEmpty() &&
                            snapshot.emergencyUnlockExpireTimeMillis > System.currentTimeMillis()
                    } == true
                if (hasActiveEmergencyUnlock) {
                    reduce { state.copy(repeatBlockRoutineSuggestion = null) }
                    return@intent
                }

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

        // init 및 홈 ON_RESUME(권한 딥링크 복귀 등)에서 호출된다. 상태 재평가는 매번 수행하되
        // shown 이벤트는 카드 식별자가 바뀔 때만 로깅해 중복 발화를 막는다.
        internal fun loadUsageInsightCard() =
            intent {
                val previousCard = state.usageInsightCard
                val result = usageInsightRepository.currentInsightCard(LocalDate.now())
                // 사용 접근 권한은 홈 첫 진입에서 요구할 이유가 없다. 아직 한 번도 잠가보지 않은
                // 사용자에게는 권한의 대가로 보여줄 인사이트도 없고, 정작 필요한 행동(차단 대상
                // 고르기·첫 잠금)과 경쟁만 한다. 첫 잠금을 경험한 뒤에 묻는다.
                val hasExperiencedFirstLock =
                    blockingStateStore.readSelectionState().hasTrackedFirstLockConfigured
                val cardState = when (result) {
                    is UsageInsightCardResult.Hidden -> UsageInsightCardUiState.Hidden
                    is UsageInsightCardResult.PermissionNeeded ->
                        if (hasExperiencedFirstLock) {
                            UsageInsightCardUiState.PermissionNeeded
                        } else {
                            UsageInsightCardUiState.Hidden
                        }
                    is UsageInsightCardResult.Ready ->
                        UsageInsightCardUiState.Insight(result.insight, result.appLabel)
                }
                reduce { state.copy(usageInsightCard = cardState) }

                // 권한 전환 전환(conversion) 이벤트: 직전 카드가 PermissionNeeded 였는데 이번 결과가
                // PermissionNeeded 가 아니면(=권한 허용됨) 전환당 1회만 로깅한다.
                if (previousCard is UsageInsightCardUiState.PermissionNeeded &&
                    result !is UsageInsightCardResult.PermissionNeeded
                ) {
                    analytics.logEvent(USAGE_INSIGHT_PERMISSION_GRANTED, emptyMap())
                }

                val cardKey = when (cardState) {
                    is UsageInsightCardUiState.PermissionNeeded -> USAGE_INSIGHT_PERMISSION_NEEDED
                    is UsageInsightCardUiState.Insight -> cardState.insight.type.analyticsValue
                    is UsageInsightCardUiState.Hidden -> null
                }
                if (cardKey != null && cardKey != lastShownCardKey) {
                    analytics.logEvent(
                        USAGE_INSIGHT_CARD_SHOWN,
                        mapOf(INSIGHT_TYPE to cardKey),
                    )
                    if (cardState is UsageInsightCardUiState.PermissionNeeded) {
                        usageInsightRepository.recordPermissionCardShown()
                    }
                }
                lastShownCardKey = cardKey
            }

        internal fun onUsageInsightCtaClick() =
            intent {
                when (val card = state.usageInsightCard) {
                    is UsageInsightCardUiState.PermissionNeeded -> {
                        analytics.logEvent(
                            USAGE_INSIGHT_CARD_CTA,
                            mapOf(INSIGHT_TYPE to USAGE_INSIGHT_PERMISSION_NEEDED),
                        )
                        postSideEffect(HomeSideEffect.OpenUsageAccessSettings)
                    }
                    is UsageInsightCardUiState.Insight -> {
                        analytics.logEvent(
                            USAGE_INSIGHT_CARD_CTA,
                            mapOf(INSIGHT_TYPE to card.insight.type.analyticsValue),
                        )
                        postSideEffect(
                            HomeSideEffect.NavigateToRoutineWithUsageInsightPrefill(
                                card.insight.toRoutinePrefill(),
                            ),
                        )
                    }
                    is UsageInsightCardUiState.Hidden -> Unit
                }
            }

        internal fun onUsageInsightDismiss() =
            intent {
                when (val card = state.usageInsightCard) {
                    is UsageInsightCardUiState.PermissionNeeded -> {
                        analytics.logEvent(
                            USAGE_INSIGHT_CARD_DISMISSED,
                            mapOf(INSIGHT_TYPE to USAGE_INSIGHT_PERMISSION_NEEDED),
                        )
                        usageInsightRepository.dismissPermissionCard(LocalDate.now())
                    }
                    is UsageInsightCardUiState.Insight -> {
                        analytics.logEvent(
                            USAGE_INSIGHT_CARD_DISMISSED,
                            mapOf(INSIGHT_TYPE to card.insight.type.analyticsValue),
                        )
                        usageInsightRepository.dismiss(card.insight.type, LocalDate.now())
                    }
                    is UsageInsightCardUiState.Hidden -> Unit
                }
                // dismiss 후 동일 카드가 재노출되면 shown 을 다시 로깅하도록 식별자를 초기화한다.
                lastShownCardKey = null
                reduce { state.copy(usageInsightCard = UsageInsightCardUiState.Hidden) }
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
                        selectedWebDomains = selectionState.selectedWebDomains,
                        blockingTargetsLoaded = true,
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
                routineRepository.fetchAll().collect { routines ->
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
                            routineWebsiteSession = routines.toRoutineWebsiteSession(),
                            routineWebsiteSessionLoaded = true,
                        )
                    }
                }
            }

        /**
         * 창은 시간이 지나면서 시작되고 끝난다. 목록이 바뀔 때만 읽으면, 화면을 열어둔 채
         * 시작 시각을 넘긴 루틴이나 알람이 누락된 회차를 놓친다.
         */
        internal fun refreshRoutineWebsiteSession() =
            intent {
                val routines = routineRepository.fetchAll().firstOrNull().orEmpty()
                reduce {
                    state.copy(
                        routineWebsiteSession = routines.toRoutineWebsiteSession(),
                        routineWebsiteSessionLoaded = true,
                    )
                }
            }

        private fun List<RoutineModel>.toRoutineWebsiteSession(): RoutineWebsiteBlockingSession? =
            RoutineWebsiteBlockingPolicy.resolveSession(toRoutineWebsiteWindows())

        private fun syncRoutinesCount() =
            intent {
                routineCountAnalyticsSync.syncFromRepository()
            }

        private fun getGoalLockCard() =
            intent {
                goalLockRepository.fetchAll().collect { goalLocks ->
                    val now = LocalDateTime.now()
                    val today = now.toLocalDate()
                    val card = selectGoalLockForHomeCard(goalLocks, now)
                        ?.toHomeGoalLockCardState(today, now)
                    reduce { state.copy(goalLockCard = card) }
                }
            }

        private fun selectGoalLockForHomeCard(
            goalLocks: List<GoalLock>,
            now: LocalDateTime,
        ): HomeGoalLockCardCandidate? {
            val candidates = goalLocks.map { goalLock ->
                val today = now.toLocalDate()
                val normalizedGoalLock = completeExpiredGoalLockIfNeeded(goalLock, today)
                HomeGoalLockCardCandidate(
                    goalLock = normalizedGoalLock,
                    runtimeStatus = GoalLockPolicy.runtimeStatus(normalizedGoalLock, today.atStartOfDay()),
                )
            }

            val currentGoalLock = GoalLockPolicy.currentGoalLock(
                goalLocks = candidates.map(HomeGoalLockCardCandidate::goalLock),
                now = now,
            )
            if (currentGoalLock != null) {
                return candidates.first { it.goalLock.id == currentGoalLock.id }
            }

            return candidates.minWithOrNull(
                compareBy<HomeGoalLockCardCandidate> { it.runtimeStatus.homeCardPriority }
                    .thenBy { it.homeCardSecondarySortKey }
                    .thenBy { it.goalLock.id },
            )
        }

        private fun HomeGoalLockCardCandidate.toHomeGoalLockCardState(
            today: LocalDate,
            now: LocalDateTime,
        ): HomeGoalLockCardState =
            HomeGoalLockCardState(
                goalLockId = goalLock.id,
                goalName = goalLock.goalName,
                status = runtimeStatus.toHomeStatus(),
                daysRemaining = ChronoUnit.DAYS.between(today, goalLock.endDate).toInt().plus(1).coerceAtLeast(0),
                lockMode = goalLock.lockMode.toHomeCardLockMode(),
                selectedAppCount = goalLock.selectedPackages.size,
                isCurrentlyProtecting = GoalLockPolicy.isCurrentlyProtecting(goalLock, now),
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
            selectLockTargetsComplete(
                selectedAppPackages = selectedAppPackage,
                selectedWebDomains = container.stateFlow.value.selectedWebDomains,
            )

        internal fun selectLockTargetsComplete(
            selectedAppPackages: Set<String>,
            selectedWebDomains: Set<String>,
        ) =
            intent {
                if (state.isKeep || activeTimedLockExists()) return@intent
                analytics.trackAppSelectionCompleted(
                    selectedAppCount = selectedAppPackages.size,
                    isOnboarding = false,
                )
                val newlySelectedPackages = selectedAppPackages - state.selectedAppPackage
                val recommendations = WebsiteLockRecommendationPolicy.recommend(
                    newlySelectedPackages = newlySelectedPackages,
                    alreadyBlockedDomains = selectedWebDomains.map(::DomainName).toSet(),
                )
                blockingStateStore.saveSelectedAppPackages(selectedAppPackages)
                blockingStateStore.saveSelectedWebDomains(selectedWebDomains)
                val hasTrackedFirstLock = blockingStateStore.readSelectionState().hasTrackedFirstLockConfigured
                val firstCoreActionState = blockingStateStore.readFirstCoreActionState(
                    fallbackFirstOpenTimestampMillis = System.currentTimeMillis(),
                )
                val showRoutineCreationCta = shouldShowRoutineCreationCta(
                    selectedAppPackage = selectedAppPackages,
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
                        selectedAppPackage = selectedAppPackages,
                        selectedWebDomains = selectedWebDomains,
                        pendingWebsiteRecommendations = recommendations,
                        showFirstLockActivationCta = shouldShowFirstLockActivationCta(
                            selectedAppPackage = selectedAppPackages,
                            hasTrackedFirstLock = hasTrackedFirstLock,
                            isKeep = state.isKeep,
                        ),
                        showRoutineCreationCta = showRoutineCreationCta,
                    )
                }
            }

        internal fun acceptWebsiteLockRecommendations() =
            intent {
                if (state.pendingWebsiteRecommendations.isEmpty()) return@intent
                val recommendedDomains = state.pendingWebsiteRecommendations
                    .flatMap { it.domains }
                    .map { it.value }
                    .toSet()
                val selectedWebDomains = state.selectedWebDomains + recommendedDomains
                blockingStateStore.saveSelectedWebDomains(selectedWebDomains)
                reduce {
                    state.copy(
                        selectedWebDomains = selectedWebDomains,
                        pendingWebsiteRecommendations = emptyList(),
                    )
                }
            }

        internal fun dismissWebsiteLockRecommendations() =
            intent {
                reduce { state.copy(pendingWebsiteRecommendations = emptyList()) }
            }

        private suspend fun activeTimedLockExists(): Boolean =
            ManualLockTimePolicy.isActiveAt(blockingStateStore.readLockTime())

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
                reduce { state.copy(isKeep = isKeep, isKeepStateLoaded = true) }
                if (isKeep) {
                    getStartTime()
                }
            }

        private fun getActiveTimedLock() =
            intent {
                val storedDeadline = blockingStateStore.readLockTime()
                val isActive = ManualLockTimePolicy.isActiveAt(
                    storedDeadline = storedDeadline,
                    now = java.time.Instant.now(),
                    zone = ZoneId.systemDefault(),
                )
                reduce {
                    state.copy(
                        hasActiveTimedLock = isActive,
                        activeTimedLockStateLoaded = true,
                        activeTimedLockDeadlineMillis = if (isActive) {
                            ManualLockTimePolicy.toInstant(storedDeadline)?.toEpochMilli()
                        } else {
                            null
                        },
                    )
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
            firstLockScheduledMessage: String? = null,
        ) =
            intent {
                if (!state.hasSelectedLockTargets()) {
                    // changeIsKeep 과 같은 이유로 시트만 연다. 스낵바는 시트를 가리는 잔소리다.
                    reduce {
                        state.copy(
                            isShowCategoryBottomSheet = true,
                            sheetVisible = true,
                        )
                    }
                    return@intent
                }
                if (state.manualLockMode == ManualLockMode.COUNTDOWN && state.countdownDurationIsZero()) {
                    return@intent
                }
                val targetLockDateTime = if (state.manualLockMode == ManualLockMode.COUNTDOWN) {
                    calculateCountdownTargetDateTime(state.countdownDays, state.countdownTime)
                } else {
                    calculateTargetLockDateTime(state.blockTime)
                }
                val targetLockInstant = targetLockDateTime.atZone(ZoneId.systemDefault()).toInstant()
                val lockedDurationMinutes = if (state.manualLockMode == ManualLockMode.COUNTDOWN) {
                    state.countdownDurationMinutes()
                } else {
                    Duration
                        .between(java.time.Instant.now(), targetLockInstant)
                        .toMillis()
                        .coerceAtLeast(0L) / 60_000L
                }
                val scheduleType = if (state.manualLockMode == ManualLockMode.COUNTDOWN) {
                    TimedLockHomeScheduleType.Countdown
                } else {
                    TimedLockHomeScheduleType.Timer
                }
                val startResult = timedLockStarter.start(
                    packages = state.selectedAppPackage,
                    durationMinutes = lockedDurationMinutes,
                    origin = TimedLockStartOrigin.Home(scheduleType),
                    targetDeadline = targetLockInstant,
                    hasWebTargets = state.selectedWebDomains.isNotEmpty(),
                )
                if (startResult !is TimedLockStartResult.Started) return@intent
                timedLockStarter.commit(startResult)
                reduce {
                    state.copy(
                        pendingManualLockRouteDeadline = startResult.encodedDeadline,
                        hasActiveTimedLock = true,
                        activeTimedLockDeadlineMillis = targetLockInstant.toEpochMilli(),
                    )
                }
                if (startResult.firstLockConfigured) {
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
            }

        private suspend fun trackFirstLockConfiguredIfNeeded(source: String): Boolean {
            val selectedAppCount = blockingStateStore.readSelectedAppPackages().size
            return firstLockDelivery.trackIfNeeded(
                source = source,
                selectedAppCount = selectedAppCount,
            )
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
    val selectedWebDomains: Set<String> = emptySet(),
    val pendingWebsiteRecommendations: List<WebsiteLockRecommendation> = emptyList(),
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
    val hasActiveTimedLock: Boolean = false,
    val activeTimedLockDeadlineMillis: Long? = null,
    val blockingTargetsLoaded: Boolean = false,
    val isKeepStateLoaded: Boolean = false,
    val activeTimedLockStateLoaded: Boolean = false,
    // 지금 서 있어야 할 루틴 창. 알람이 지연·누락되었거나 창 도중 재부팅한 회차는
    // 이 값으로만 되살아난다.
    val routineWebsiteSession: RoutineWebsiteBlockingSession? = null,
    val routineWebsiteSessionLoaded: Boolean = false,
    val goalLockCard: HomeGoalLockCardState? = null,
    val repeatBlockRoutineSuggestion: RepeatBlockRoutineSuggestion? = null,
    val usageInsightCard: UsageInsightCardUiState = UsageInsightCardUiState.Hidden,
    val firstPromiseResumeCard: FirstPromiseResumeCardState? = null,
) {
    fun hasSelectedLockTargets(): Boolean =
        selectedAppPackage.isNotEmpty() || selectedWebDomains.isNotEmpty()

    fun websiteBlockingRuntimeDecision(): WebsiteBlockingRuntimeDecision =
        WebsiteBlockingRuntimePolicy.decide(
            runtimeStateLoaded = websiteBlockingRuntimeStateLoaded(),
            isKeep = isKeep,
            hasActiveTimedLock = hasActiveTimedLock,
            timedLockDeadlineMillis = activeTimedLockDeadlineMillis,
            selectedWebDomains = selectedWebDomains,
            routineSession = routineWebsiteSession,
        )

    fun lockTargetKind(): LockTargetKind =
        LockTargetKind.of(
            hasApps = selectedAppPackage.isNotEmpty(),
            hasWebsites = selectedWebDomains.isNotEmpty(),
        )

    fun websiteBlockingRuntimeStateLoaded(): Boolean =
        blockingTargetsLoaded &&
            isKeepStateLoaded &&
            activeTimedLockStateLoaded &&
            routineWebsiteSessionLoaded

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
    val lockMode: HomeGoalLockCardLockMode,
    val selectedAppCount: Int,
    val isCurrentlyProtecting: Boolean = false,
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

enum class HomeGoalLockCardLockMode {
    AllDay,
    Scheduled,
}

private fun GoalLockMode.toHomeCardLockMode(): HomeGoalLockCardLockMode = when (this) {
    GoalLockMode.AllDay -> HomeGoalLockCardLockMode.AllDay
    is GoalLockMode.Scheduled -> HomeGoalLockCardLockMode.Scheduled
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
        val drainNextRoutineStartNoticeAfterDismiss: Boolean = false,
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

    data class NavigateToRoutineWithUsageInsightPrefill(
        val prefill: UsageInsightRoutinePrefill,
    ) : HomeSideEffect()

    data object OpenUsageAccessSettings : HomeSideEffect()
    data object OpenExactAlarmSettings : HomeSideEffect()
}

private const val USAGE_INSIGHT_CARD_SHOWN = "usage_insight_card_shown"
private const val USAGE_INSIGHT_CARD_CTA = "usage_insight_card_cta"
private const val USAGE_INSIGHT_CARD_DISMISSED = "usage_insight_card_dismissed"
private const val USAGE_INSIGHT_PERMISSION_GRANTED = "usage_insight_permission_granted"
private const val INSIGHT_TYPE = "insight_type"
private const val USAGE_INSIGHT_PERMISSION_NEEDED = "permission_needed"

package com.uiery.keep.feature.onboarding.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.data.firstpromise.FirstPromiseCreationResult
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceCoordinator
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceResult
import com.uiery.keep.data.firstpromise.FirstPromisePracticeController
import com.uiery.keep.data.firstpromise.FirstPromisePracticeStartResult
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.PendingSystemAction
import com.uiery.keep.feature.review.AccessibilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

enum class PromiseResultKind { Loading, Enabled, Disabled, PermissionRequired, PersistFailed }

data class PromiseResultUiState(
    val kind: PromiseResultKind = PromiseResultKind.Loading,
    val appLabel: String = "",
    val startMinutes: Int = 0,
    val repeatDays: Set<Int> = emptySet(),
    val showPractice: Boolean = false,
    val showScheduledGuidance: Boolean = false,
    val canEdit: Boolean = false,
    val canRetry: Boolean = false,
    val isBusy: Boolean = false,
    val practiceFailed: Boolean = false,
    val activationFailed: Boolean = false,
)

sealed interface PromiseResultSideEffect {
    data object OpenExactAlarmSettings : PromiseResultSideEffect
    data object NavigateProposal : PromiseResultSideEffect
    data object NavigateHome : PromiseResultSideEffect
    data class NavigateLock(val lockTime: String?) : PromiseResultSideEffect
}

@HiltViewModel
class PromiseResultViewModel internal constructor(
    private val draftStore: FirstPromiseDraftStore,
    private val blockingStateStore: BlockingStateStore,
    private val analyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
    private val coordinator: FirstPromisePersistenceCoordinator,
    private val isAccessibilityGranted: () -> Boolean,
    private val hasActiveTimedLock: suspend () -> Boolean,
    private val canScheduleExactAlarms: () -> Boolean,
    private val startPractice: suspend (FirstPromiseDraft, Boolean) -> FirstPromisePracticeStartResult,
    private val skipPractice: suspend (String, Boolean) -> Unit,
    private val dispatcher: CoroutineDispatcher,
    private val mainDispatcher: CoroutineDispatcher,
    private val trackScreen: () -> Unit,
) : ViewModel(), ContainerHost<PromiseResultUiState, PromiseResultSideEffect> {
    @Inject constructor(
        draftStore: FirstPromiseDraftStore,
        blockingStateStore: BlockingStateStore,
        analyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
        coordinator: FirstPromisePersistenceCoordinator,
        practiceController: FirstPromisePracticeController,
        accessibilityChecker: AccessibilityChecker,
        exactAlarmOrchestrator: com.uiery.keep.data.routine.RoutineExactAlarmOrchestrator,
        analytics: KeepAnalytics,
    ) : this(
        draftStore = draftStore,
        blockingStateStore = blockingStateStore,
        analyticsDispatcher = analyticsDispatcher,
        coordinator = coordinator,
        isAccessibilityGranted = accessibilityChecker::isEnabled,
        hasActiveTimedLock = {
            ManualLockTimePolicy.isActiveAt(
                storedDeadline = blockingStateStore.readLockTime(),
                now = Instant.now(),
                zone = ZoneId.systemDefault(),
            )
        },
        canScheduleExactAlarms = exactAlarmOrchestrator::canScheduleExactAlarms,
        startPractice = practiceController::start,
        skipPractice = practiceController::skip,
        dispatcher = Dispatchers.IO,
        mainDispatcher = Dispatchers.Main.immediate,
        trackScreen = { analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_PROMISE_RESULT) },
    )

    override val container: Container<PromiseResultUiState, PromiseResultSideEffect> =
        container(PromiseResultUiState())
    private val actionMutex = Mutex()
    private var navigationPosted = false

    internal constructor(
        draftStore: FirstPromiseDraftStore,
        blockingStateStore: BlockingStateStore,
        analyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
        coordinator: FirstPromisePersistenceCoordinator,
        isAccessibilityGranted: () -> Boolean,
        hasActiveTimedLock: suspend () -> Boolean,
        startPractice: suspend (FirstPromiseDraft, Boolean) -> FirstPromisePracticeStartResult,
        skipPractice: suspend (String, Boolean) -> Unit,
        dispatcher: CoroutineDispatcher,
        mainDispatcher: CoroutineDispatcher,
        canScheduleExactAlarms: () -> Boolean = { true },
    ) : this(
        draftStore,
        blockingStateStore,
        analyticsDispatcher,
        coordinator,
        isAccessibilityGranted,
        hasActiveTimedLock,
        canScheduleExactAlarms = canScheduleExactAlarms,
        startPractice = startPractice,
        skipPractice = skipPractice,
        dispatcher = dispatcher,
        mainDispatcher = mainDispatcher,
        trackScreen = {},
    )

    fun load() {
        viewModelScope.launch(dispatcher) {
            actionMutex.withLock {
                trackScreen()
                draftStore.markPromiseResultViewed()
                analyticsDispatcher.drain()
                refresh()
            }
        }
    }

    fun retryPersistence() {
        viewModelScope.launch(dispatcher) {
            actionMutex.withLock {
                if (draftStore.beginPersistence() !is FirstPromiseStateMutation.Changed) return@withLock
                setBusy(true)
                coordinator.persistCurrentDraft()
                refresh()
            }
        }
    }

    fun editPromise() {
        viewModelScope.launch(dispatcher) {
            actionMutex.withLock {
                if (draftStore.returnToDraft() is FirstPromiseStateMutation.Changed) {
                    post(PromiseResultSideEffect.NavigateProposal)
                }
            }
        }
    }

    fun requestExactAlarm() {
        viewModelScope.launch(dispatcher) {
            actionMutex.withLock {
                val state = draftStore.readState()
                if (state.phase != FirstPromisePhase.SchedulePermissionRequired) return@withLock
                if (state.pendingSystemAction == PendingSystemAction.ExactAlarm) return@withLock
                if (canScheduleExactAlarms()) {
                    finalizeExisting(checkNotNull(state.routineId))
                } else {
                    setBusy(true)
                    val mutation = draftStore.setPendingSystemAction(PendingSystemAction.ExactAlarm)
                    if (mutation is FirstPromiseStateMutation.Changed) {
                        post(PromiseResultSideEffect.OpenExactAlarmSettings)
                    } else {
                        refresh(activationFailed = true)
                    }
                }
            }
        }
    }

    fun onExactAlarmSettingsUnavailable() {
        viewModelScope.launch(dispatcher) {
            actionMutex.withLock {
                val state = draftStore.readState()
                if (
                    state.phase != FirstPromisePhase.SchedulePermissionRequired ||
                    state.pendingSystemAction != PendingSystemAction.ExactAlarm
                ) return@withLock
                draftStore.clearPendingSystemAction()
                refresh(activationFailed = true)
            }
        }
    }

    fun onResumeFromExactAlarm() {
        viewModelScope.launch(dispatcher) {
            actionMutex.withLock {
                val state = draftStore.readState()
                if (
                    state.phase != FirstPromisePhase.SchedulePermissionRequired ||
                    state.pendingSystemAction != PendingSystemAction.ExactAlarm
                ) return@withLock
                if (canScheduleExactAlarms()) {
                    finalizeExisting(checkNotNull(state.routineId))
                } else {
                    draftStore.clearPendingSystemAction()
                    refresh(activationFailed = true)
                }
            }
        }
    }

    fun continueLater() {
        viewModelScope.launch(dispatcher) {
            actionMutex.withLock {
                val state = draftStore.readState()
                val routineId = state.routineId ?: return@withLock
                val scheduleState = state.scheduleState ?: return@withLock
                if (state.phase == FirstPromisePhase.SchedulePermissionRequired) {
                    draftStore.resolveScheduleState(routineId, scheduleState)
                }
                completeAndNavigate()
            }
        }
    }

    fun continueHome() {
        viewModelScope.launch(dispatcher) {
            actionMutex.withLock { completeAndNavigate() }
        }
    }

    fun continueAtScheduledTime() {
        viewModelScope.launch(dispatcher) {
            actionMutex.withLock {
                val state = draftStore.readState()
                val draft = state.draft ?: return@withLock
                if (state.phase != FirstPromisePhase.ResultEnabled) return@withLock
                skipPractice(draft.draftId, true)
                completeAndNavigate()
            }
        }
    }

    fun startPractice() {
        viewModelScope.launch(dispatcher) {
            actionMutex.withLock {
                val state = draftStore.readState()
                val draft = state.draft ?: return@withLock
                if (state.phase != FirstPromisePhase.ResultEnabled) return@withLock
                setBusy(true)
                when (startPractice(draft, state.scheduleState == FirstPromiseScheduleState.Enabled)) {
                    FirstPromisePracticeStartResult.Started -> completeAndNavigate(
                        PromiseResultSideEffect.NavigateLock(blockingStateStore.readLockTime()),
                    )
                    else -> refresh(practiceFailed = true)
                }
            }
        }
    }

    private suspend fun finalizeExisting(routineId: Long) {
        setBusy(true)
        when (coordinator.finalizeExistingRoutine(routineId)) {
            is FirstPromisePersistenceResult.Succeeded -> refresh()
            else -> refresh(activationFailed = true)
        }
    }

    private suspend fun completeAndNavigate(
        destination: PromiseResultSideEffect = PromiseResultSideEffect.NavigateHome,
    ) {
        when (draftStore.completeOnboarding()) {
            is FirstPromiseStateMutation.Changed -> {
                analyticsDispatcher.drain()
                postNavigationOnce(destination)
            }
            FirstPromiseStateMutation.NoOp -> {
                analyticsDispatcher.drain()
                postNavigationOnce(destination)
            }
            FirstPromiseStateMutation.Rejected -> refresh()
        }
    }

    private suspend fun postNavigationOnce(destination: PromiseResultSideEffect) {
        if (navigationPosted) return
        navigationPosted = true
        post(destination)
    }

    private suspend fun refresh(
        practiceFailed: Boolean = false,
        activationFailed: Boolean = false,
    ) {
        val durable = draftStore.readState()
        val mapping = when (durable.phase) {
            FirstPromisePhase.PersistFailed -> null
            else -> coordinator.readCurrentMapping()
        }
        val draft = durable.draft
        val routine = mapping?.routine
        val kind = when (durable.phase) {
            FirstPromisePhase.ResultEnabled -> PromiseResultKind.Enabled
            FirstPromisePhase.ResultDisabled -> PromiseResultKind.Disabled
            FirstPromisePhase.SchedulePermissionRequired -> PromiseResultKind.PermissionRequired
            FirstPromisePhase.PersistFailed -> PromiseResultKind.PersistFailed
            else -> PromiseResultKind.Loading
        }
        val practiceEligible = kind == PromiseResultKind.Enabled &&
            routine?.isEnabled == true &&
            isAccessibilityGranted() &&
            !hasActiveTimedLock()
        intent {
            reduce {
                PromiseResultUiState(
                    kind = kind,
                    appLabel = draft?.appLabel ?: routine?.name.orEmpty(),
                    startMinutes = draft?.startMinutes
                        ?: routine?.startTime?.let { it.hour * 60 + it.minute }
                        ?: 0,
                    repeatDays = draft?.repeatDays ?: routine?.repeatDays?.toDaySet().orEmpty(),
                    showPractice = practiceEligible,
                    showScheduledGuidance = kind == PromiseResultKind.Enabled && !practiceEligible,
                    canEdit = kind == PromiseResultKind.PersistFailed,
                    canRetry = kind == PromiseResultKind.PersistFailed,
                    isBusy = false,
                    practiceFailed = practiceFailed,
                    activationFailed = activationFailed,
                )
            }
        }
    }

    private suspend fun setBusy(value: Boolean) {
        intent { reduce { state.copy(isBusy = value, practiceFailed = false, activationFailed = false) } }
    }

    private suspend fun post(effect: PromiseResultSideEffect) {
        withContext(mainDispatcher) { intent { postSideEffect(effect) } }
    }

}

private fun String.toDaySet(): Set<Int> = mapIndexedNotNull { index, value ->
    (index + 1).takeIf { value == '1' }
}.toSet()

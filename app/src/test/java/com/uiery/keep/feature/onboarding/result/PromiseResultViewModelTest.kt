package com.uiery.keep.feature.onboarding.result

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.data.firstpromise.FirstPromiseCreationResult
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceCoordinator
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceResult
import com.uiery.keep.data.firstpromise.FirstPromisePracticeStartResult
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.PendingSystemAction
import com.uiery.keep.feature.onboarding.FirstPromiseAnalyticsCall
import com.uiery.keep.feature.onboarding.FirstPromiseRecordingAnalytics
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.model.RoutineModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromiseResultViewModelTest {
    @Test
    fun enabledResultOffersPracticeOnlyWhenAccessibilityAndSessionAllowIt() = runBlocking {
        val fixture = fixture(FirstPromisePhase.ResultEnabled, FirstPromiseScheduleState.Enabled)

        fixture.viewModel.load()
        fixture.viewModel.awaitKind(PromiseResultKind.Enabled)

        assertEquals(PromiseResultKind.Enabled, fixture.viewModel.container.stateFlow.value.kind)
        assertTrue(fixture.viewModel.container.stateFlow.value.showPractice)
        assertEquals("YouTube", fixture.viewModel.container.stateFlow.value.appLabel)

        val blocked = fixture(
            FirstPromisePhase.ResultEnabled,
            FirstPromiseScheduleState.Enabled,
            accessibilityGranted = false,
        )
        blocked.viewModel.load()
        blocked.viewModel.awaitKind(PromiseResultKind.Enabled)
        assertFalse(blocked.viewModel.container.stateFlow.value.showPractice)
        assertTrue(blocked.viewModel.container.stateFlow.value.showScheduledGuidance)

        val activeLock = fixture(
            FirstPromisePhase.ResultEnabled,
            FirstPromiseScheduleState.Enabled,
            activeTimedLock = true,
        )
        activeLock.viewModel.load()
        activeLock.viewModel.awaitKind(PromiseResultKind.Enabled)
        assertFalse(activeLock.viewModel.container.stateFlow.value.showPractice)
        assertTrue(activeLock.viewModel.container.stateFlow.value.showScheduledGuidance)
    }

    @Test
    fun permissionRequiredLaterCompletesDisabledAndNavigatesHomeInOneAction() = runBlocking {
        val fixture = fixture(
            FirstPromisePhase.SchedulePermissionRequired,
            FirstPromiseScheduleState.DisabledExactAlarmMissing,
        )
        fixture.viewModel.load()
        fixture.viewModel.awaitKind(PromiseResultKind.PermissionRequired)
        val navigation = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.viewModel.container.sideEffectFlow.first()
        }

        fixture.viewModel.continueLater()

        assertEquals(PromiseResultSideEffect.NavigateHome, withTimeout(1_000) { navigation.await() })
        assertEquals(FirstPromisePhase.CompletedDisabled, fixture.store.readState().phase)
        assertEquals(FirstPromiseScheduleState.DisabledExactAlarmMissing, fixture.store.readState().scheduleState)
        assertFalse(fixture.blockingStateStore.readIsNew())
    }

    @Test
    fun disabledResultNeverOffersPracticeAndCompletesWithoutPracticeOutcome() = runBlocking {
        val fixture = fixture(
            FirstPromisePhase.ResultDisabled,
            FirstPromiseScheduleState.DisabledExactAlarmMissing,
        )
        fixture.viewModel.load()
        fixture.viewModel.awaitKind(PromiseResultKind.Disabled)
        fixture.viewModel.continueHome()

        assertEquals(PromiseResultKind.Disabled, fixture.viewModel.container.stateFlow.value.kind)
        assertFalse(fixture.viewModel.container.stateFlow.value.showPractice)
        assertEquals(0, fixture.practiceSkips.get())
        assertEquals(FirstPromisePhase.CompletedDisabled, fixture.store.readState().phase)
        assertFalse(fixture.blockingStateStore.readIsNew())
        assertNull(fixture.store.readState().draft)
        assertEquals(77L, fixture.store.readState().routineId)
    }

    @Test
    fun persistenceFailureKeepsDraftAndOffersEditAndRetry() = runBlocking {
        val fixture = fixture(FirstPromisePhase.PersistFailed, null)
        fixture.viewModel.load()
        fixture.viewModel.awaitKind(PromiseResultKind.PersistFailed)

        assertEquals(PromiseResultKind.PersistFailed, fixture.viewModel.container.stateFlow.value.kind)
        assertTrue(fixture.viewModel.container.stateFlow.value.canEdit)
        assertTrue(fixture.viewModel.container.stateFlow.value.canRetry)
        assertEquals("draft", fixture.store.readState().draft?.draftId)
    }

    @Test
    fun exactAlarmResumeFinalizesTheSameRoutineId() = runBlocking {
        val fixture = fixture(
            FirstPromisePhase.SchedulePermissionRequired,
            FirstPromiseScheduleState.DisabledExactAlarmMissing,
            canScheduleExactAlarms = false,
        )
        fixture.viewModel.load()
        fixture.viewModel.requestExactAlarm()
        assertEquals(PendingSystemAction.ExactAlarm, fixture.store.readState().pendingSystemAction)
        fixture.canScheduleExactAlarms.set(true)
        fixture.viewModel.onResumeFromExactAlarm()

        assertEquals(listOf(77L), fixture.coordinator.finalizedIds)
        assertEquals(FirstPromisePhase.ResultEnabled, fixture.store.readState().phase)
        assertEquals(77L, fixture.store.readState().routineId)
        assertNull(fixture.store.readState().pendingSystemAction)
    }

    @Test
    fun rapidExactAlarmRequestsOpenSettingsOnceAndRemainBusyUntilDecision() = runBlocking {
        val fixture = fixture(
            FirstPromisePhase.SchedulePermissionRequired,
            FirstPromiseScheduleState.DisabledExactAlarmMissing,
            canScheduleExactAlarms = false,
        )
        fixture.viewModel.load()
        fixture.viewModel.awaitKind(PromiseResultKind.PermissionRequired)
        val effects = mutableListOf<PromiseResultSideEffect>()
        val collector = launch {
            fixture.viewModel.container.sideEffectFlow.collect(effects::add)
        }

        fixture.viewModel.requestExactAlarm()
        fixture.viewModel.requestExactAlarm()

        withTimeout(1_000) {
            while (effects.count { it == PromiseResultSideEffect.OpenExactAlarmSettings } < 1) yield()
        }
        delay(100)
        assertEquals(1, effects.count { it == PromiseResultSideEffect.OpenExactAlarmSettings })
        assertTrue(fixture.viewModel.container.stateFlow.value.isBusy)
        assertEquals(PendingSystemAction.ExactAlarm, fixture.store.readState().pendingSystemAction)
        collector.cancel()
    }

    @Test
    fun unavailableExactAlarmSettingsClearsBusyAndAllowsRetry() = runBlocking {
        val fixture = fixture(
            FirstPromisePhase.SchedulePermissionRequired,
            FirstPromiseScheduleState.DisabledExactAlarmMissing,
            canScheduleExactAlarms = false,
        )
        fixture.viewModel.load()
        fixture.viewModel.awaitKind(PromiseResultKind.PermissionRequired)
        val firstEffect = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.viewModel.container.sideEffectFlow.first()
        }
        fixture.viewModel.requestExactAlarm()
        assertEquals(PromiseResultSideEffect.OpenExactAlarmSettings, withTimeout(1_000) { firstEffect.await() })

        fixture.viewModel.onExactAlarmSettingsUnavailable()

        withTimeout(1_000) {
            fixture.viewModel.container.stateFlow.first { it.activationFailed && !it.isBusy }
        }
        assertNull(fixture.store.readState().pendingSystemAction)
        val retryEffect = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.viewModel.container.sideEffectFlow.first()
        }
        fixture.viewModel.requestExactAlarm()
        assertEquals(PromiseResultSideEffect.OpenExactAlarmSettings, withTimeout(1_000) { retryEffect.await() })
    }

    @Test
    fun resultViewAndTerminalCompletionAreDurableAndDeliveredOnce() = runBlocking {
        val fixture = fixture(FirstPromisePhase.ResultEnabled, FirstPromiseScheduleState.Enabled)

        fixture.viewModel.load()
        fixture.viewModel.load()
        fixture.viewModel.continueAtScheduledTime()
        fixture.viewModel.continueAtScheduledTime()

        assertEquals(1, fixture.analytics.calls.count {
            it == FirstPromiseAnalyticsCall.StepView("promise_result")
        })
        assertEquals(1, fixture.analytics.calls.count {
            it == FirstPromiseAnalyticsCall.StepComplete("promise_result")
        })
        assertEquals(1, fixture.practiceSkips.get())
    }

    @Test
    fun practiceFailureStaysRetryableAndSuccessCompletes() = runBlocking {
        val fixture = fixture(
            FirstPromisePhase.ResultEnabled,
            FirstPromiseScheduleState.Enabled,
            practiceResults = ArrayDeque(
                listOf(FirstPromisePracticeStartResult.Failed, FirstPromisePracticeStartResult.Started),
            ),
        )
        fixture.viewModel.load()
        fixture.viewModel.awaitKind(PromiseResultKind.Enabled)
        fixture.viewModel.startPractice()
        withTimeout(1_000) { fixture.viewModel.container.stateFlow.first { it.practiceFailed } }
        assertTrue(fixture.viewModel.container.stateFlow.value.practiceFailed)

        fixture.viewModel.startPractice()
        assertEquals(FirstPromisePhase.CompletedEnabled, fixture.store.readState().phase)
        assertFalse(fixture.blockingStateStore.readIsNew())
    }
}

private suspend fun PromiseResultViewModel.awaitKind(kind: PromiseResultKind) {
    withTimeout(1_000) { container.stateFlow.first { it.kind == kind } }
}

private data class ResultFixture(
    val viewModel: PromiseResultViewModel,
    val store: FirstPromiseDraftStore,
    val blockingStateStore: BlockingStateStore,
    val analytics: FirstPromiseRecordingAnalytics,
    val coordinator: FakeResultCoordinator,
    val practiceSkips: AtomicInteger,
    val canScheduleExactAlarms: AtomicBoolean,
)

private fun fixture(
    phase: FirstPromisePhase,
    scheduleState: FirstPromiseScheduleState?,
    accessibilityGranted: Boolean = true,
    activeTimedLock: Boolean = false,
    canScheduleExactAlarms: Boolean = true,
    practiceResults: ArrayDeque<FirstPromisePracticeStartResult> = ArrayDeque(
        listOf(FirstPromisePracticeStartResult.Started),
    ),
): ResultFixture {
    val draft = FirstPromiseDraft(
        draftId = "draft",
        goal = FirstPromiseGoal.Focus,
        packageName = "video.app",
        appLabel = "YouTube",
        startMinutes = 23 * 60,
        repeatDays = setOf(1, 3, 5),
        source = FirstPromiseSource.Manual,
    )
    val state = FirstPromiseOnboardingState(
        assignment = OnboardingVariant.PromiseCoachV1,
        assignmentVersion = OnboardingAssignmentVersion.V1,
        phase = phase,
        path = FirstPromisePath.Manual,
        goal = FirstPromiseGoal.Focus,
        draft = draft,
        routineId = if (scheduleState == null) null else 77L,
        scheduleState = scheduleState,
    )
    val dataStore = FakeDataStore(
        mutablePreferencesOf(
            PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
            PreferencesKey.IS_NEW to true,
        ),
    )
    val store = FirstPromiseDraftStore(dataStore)
    val analytics = FirstPromiseRecordingAnalytics()
    val coordinator = FakeResultCoordinator(store, draft)
    val practiceSkips = AtomicInteger()
    val exactAlarmAvailable = AtomicBoolean(canScheduleExactAlarms)
    val viewModel = PromiseResultViewModel(
        draftStore = store,
        blockingStateStore = BlockingStateStore(dataStore),
        analyticsDispatcher = FirstPromiseOnboardingAnalyticsDispatcher(store, analytics),
        coordinator = coordinator,
        isAccessibilityGranted = { accessibilityGranted },
        hasActiveTimedLock = { activeTimedLock },
        startPractice = { _, _ -> practiceResults.removeFirst() },
        skipPractice = { _, _ -> practiceSkips.incrementAndGet() },
        dispatcher = Dispatchers.Unconfined,
        mainDispatcher = Dispatchers.Unconfined,
        canScheduleExactAlarms = exactAlarmAvailable::get,
    )
    return ResultFixture(
        viewModel,
        store,
        BlockingStateStore(dataStore),
        analytics,
        coordinator,
        practiceSkips,
        exactAlarmAvailable,
    )
}

private class FakeResultCoordinator(
    private val store: FirstPromiseDraftStore,
    private val draft: FirstPromiseDraft,
) : FirstPromisePersistenceCoordinator {
    val finalizedIds = mutableListOf<Long>()
    private val enabledRoutine = routine(enabled = true)

    override suspend fun persistCurrentDraft(): FirstPromisePersistenceResult =
        FirstPromisePersistenceResult.Succeeded(creation(enabledRoutine, FirstPromiseScheduleState.Enabled))

    override suspend fun readCurrentMapping(): FirstPromiseCreationResult? = when (store.readState().scheduleState) {
        FirstPromiseScheduleState.Enabled -> creation(enabledRoutine, FirstPromiseScheduleState.Enabled)
        null -> null
        else -> creation(routine(enabled = false), checkNotNull(store.readState().scheduleState))
    }

    override suspend fun reconcileExistingRoutine(routineId: Long): FirstPromisePersistenceResult =
        readCurrentMapping()?.let(FirstPromisePersistenceResult::Succeeded)
            ?: FirstPromisePersistenceResult.MissingRoutine

    override suspend fun finalizeExistingRoutine(routineId: Long): FirstPromisePersistenceResult {
        finalizedIds += routineId
        store.resolveScheduleState(routineId, FirstPromiseScheduleState.Enabled)
        return FirstPromisePersistenceResult.Succeeded(creation(enabledRoutine, FirstPromiseScheduleState.Enabled))
    }

    private fun creation(routine: RoutineModel, state: FirstPromiseScheduleState) = FirstPromiseCreationResult(
        routineId = 77L,
        routine = routine,
        scheduleState = state,
        schedulingSucceeded = state == FirstPromiseScheduleState.Enabled,
        created = false,
        draftId = draft.draftId,
    )
}

private fun routine(enabled: Boolean) = RoutineModel(
    id = 77L,
    name = "YouTube",
    startTime = LocalTime(23, 0),
    endTime = LocalTime(23, 30),
    repeatDays = "1010100",
    lockApplications = listOf("video.app"),
    isEnabled = enabled,
)

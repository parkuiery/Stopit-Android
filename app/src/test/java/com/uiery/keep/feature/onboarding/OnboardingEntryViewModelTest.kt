package com.uiery.keep.feature.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.FirstPromiseStateReadResult
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromisePersistenceResolution
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.PendingOnboardingAnalyticsEvent
import com.uiery.keep.feature.onboarding.entry.OnboardingEntryDestination
import com.uiery.keep.feature.onboarding.entry.OnboardingEntrySideEffect
import com.uiery.keep.feature.onboarding.entry.OnboardingEntryViewModel
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentConfig
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentResolution
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingEntryViewModelTest {
    @Test
    fun entryDrainsPendingOnboardingAnalyticsBeforeRoutingAfterRecreation() = runBlocking {
        val analytics = FirstPromiseRecordingAnalytics()
        val dataStore = stateDataStore(
            assigned(OnboardingVariant.Control).copy(
                trackedMilestones = setOf(FirstPromiseMilestone.Exposure),
                pendingOnboardingAnalyticsEvents = listOf(
                    PendingOnboardingAnalyticsEvent.ExperimentExposureControlV1,
                ),
            ),
        )
        val store = FirstPromiseDraftStore(dataStore)
        val viewModel = viewModel(
            dataStore = dataStore,
            onboardingAnalyticsDispatcher = FirstPromiseOnboardingAnalyticsDispatcher(store, analytics),
        )
        val navigation = async { viewModel.container.sideEffectFlow.first() }

        viewModel.resolve()

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro),
            navigation.await(),
        )
        assertEquals(
            listOf(FirstPromiseAnalyticsCall.Exposure(OnboardingVariant.Control)),
            analytics.calls,
        )
        assertTrue(store.readState().pendingOnboardingAnalyticsEvents.isEmpty())
    }

    @Test
    fun malformedPersistedStateFailsClosedWithoutAssignmentWriteOrNavigation() = runBlocking {
        val malformed = "{not-valid-json"
        val dataStore = CountingDataStore(
            mutablePreferencesOf(PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to malformed),
        )
        val experimentConfig = CountingExperimentConfig(readableTreatment())
        val viewModel = viewModel(
            dataStore = dataStore,
            experimentConfig = experimentConfig,
        )

        val effect = async { viewModel.container.sideEffectFlow.first() }
        viewModel.resolve()
        val emittedEffect = withTimeout(1_000L) { effect.await() }

        assertEquals(OnboardingEntrySideEffect.StateCorrupted, emittedEffect)
        assertFalse(emittedEffect is OnboardingEntrySideEffect.Navigate)
        assertEquals(0, experimentConfig.resolveCount)
        assertEquals(0, dataStore.updateCount)
        assertEquals(malformed, dataStore.snapshot()[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])
        assertEquals(
            FirstPromiseStateReadResult.Corrupted,
            FirstPromiseDraftStore(dataStore).observeStateResult().first(),
        )
    }

    @Test
    fun freshInstallUsesReadableTreatmentResolutionAndPersistsIt() = runBlocking {
        val dataStore = FakeDataStore()
        val experimentConfig = CountingExperimentConfig(readableTreatment())

        val effect = viewModel(
            dataStore = dataStore,
            experimentConfig = experimentConfig,
        ).resolveEntry()

        assertEquals(OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.GoalSelect), effect)
        assertEquals(OnboardingVariant.PromiseCoachV1, FirstPromiseDraftStore(dataStore).readState().assignment)
        assertEquals(1, experimentConfig.resolveCount)
    }

    @Test
    fun freshInstallUsesUnreadableControlResolutionAndPersistsControl() = runBlocking {
        val dataStore = FakeDataStore()
        val experimentConfig = CountingExperimentConfig(OnboardingExperimentResolution())

        val effect = viewModel(
            dataStore = dataStore,
            experimentConfig = experimentConfig,
        ).resolveEntry()

        assertEquals(OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro), effect)
        assertEquals(OnboardingVariant.Control, FirstPromiseDraftStore(dataStore).readState().assignment)
        assertEquals(1, experimentConfig.resolveCount)
    }

    @Test
    fun entryPublishesOnlyOneRoutingDecision() = runBlocking {
        val viewModel = viewModel(state = assigned(OnboardingVariant.Control))
        val firstEffect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.resolve()
        viewModel.resolve()

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro),
            firstEffect.await(),
        )
        assertEquals(null, withTimeoutOrNull(100L) { viewModel.container.sideEffectFlow.first() })
    }

    @Test
    fun persistingWaitsWithoutNavigationUntilMappingSuccessThenRoutesResultOnce() = runBlocking {
        val dataStore = stateDataStore(
            assigned(OnboardingVariant.PromiseCoachV1).copy(phase = FirstPromisePhase.Persisting),
        )
        val experimentConfig = CountingExperimentConfig(readableControl())
        val viewModel = viewModel(dataStore = dataStore, experimentConfig = experimentConfig)
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.resolve()
        delay(100)
        assertFalse(effect.isCompleted)

        FirstPromiseDraftStore(dataStore).resolveEmergencyPersistence(
            FirstPromisePersistenceResolution.Succeeded(
                routineId = 42L,
                scheduleState = FirstPromiseScheduleState.Enabled,
            ),
        )

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.PromiseResult),
            withTimeout(1_000L) { effect.await() },
        )
        assertEquals(1, experimentConfig.resolveCount)
        viewModel.resolve()
        assertEquals(null, withTimeoutOrNull(100L) { viewModel.container.sideEffectFlow.first() })
    }

    @Test
    fun persistingWaitsWithoutNavigationUntilFailureThenRoutesManualOnce() = runBlocking {
        val dataStore = stateDataStore(
            assigned(OnboardingVariant.PromiseCoachV1).copy(phase = FirstPromisePhase.Persisting),
        )
        val experimentConfig = CountingExperimentConfig(readableControl())
        val viewModel = viewModel(dataStore = dataStore, experimentConfig = experimentConfig)
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.resolve()
        delay(100)
        assertFalse(effect.isCompleted)

        FirstPromiseDraftStore(dataStore).resolveEmergencyPersistence(
            FirstPromisePersistenceResolution.Failed,
        )

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.ManualAppSelect),
            withTimeout(1_000L) { effect.await() },
        )
        assertEquals(1, experimentConfig.resolveCount)
        viewModel.resolve()
        assertEquals(null, withTimeoutOrNull(100L) { viewModel.container.sideEffectFlow.first() })
    }

    @Test
    fun existingControlRoutesIntroWithoutResolvingExperiment() = runBlocking {
        val experimentConfig = CountingExperimentConfig(readableTreatment())

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro),
            viewModel(
                state = assigned(OnboardingVariant.Control),
                experimentConfig = experimentConfig,
            ).resolveEntry(),
        )
        assertEquals(0, experimentConfig.resolveCount)
    }

    @Test
    fun existingControlStaysControlEvenWhenTreatmentIsAvailable() = runBlocking {
        val dataStore = stateDataStore(assigned(OnboardingVariant.Control))
        val experimentConfig = CountingExperimentConfig(readableTreatment())

        val effect = viewModel(
            dataStore = dataStore,
            experimentConfig = experimentConfig,
        ).resolveEntry()

        assertEquals(OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro), effect)
        assertEquals(OnboardingVariant.Control, FirstPromiseDraftStore(dataStore).readState().assignment)
        assertEquals(0, experimentConfig.resolveCount)
    }

    @Test
    fun existingTreatmentWithUnreadableControlFallbackPreservesTreatment() = runBlocking {
        val dataStore = stateDataStore(assigned(OnboardingVariant.PromiseCoachV1))
        val experimentConfig = CountingExperimentConfig(OnboardingExperimentResolution())

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.GoalSelect),
            viewModel(dataStore = dataStore, experimentConfig = experimentConfig).resolveEntry(),
        )
        assertEquals(OnboardingVariant.PromiseCoachV1, FirstPromiseDraftStore(dataStore).readState().assignment)
        assertEquals(1, experimentConfig.resolveCount)
    }

    @Test
    fun existingTreatmentWithReadableTreatmentContinuesTreatment() = runBlocking {
        val dataStore = stateDataStore(assigned(OnboardingVariant.PromiseCoachV1))
        val experimentConfig = CountingExperimentConfig(readableTreatment())

        val effect = viewModel(dataStore = dataStore, experimentConfig = experimentConfig).resolveEntry()

        assertEquals(OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.GoalSelect), effect)
        assertEquals(OnboardingVariant.PromiseCoachV1, FirstPromiseDraftStore(dataStore).readState().assignment)
        assertEquals(1, experimentConfig.resolveCount)
    }

    @Test
    fun everyPersistedTreatmentPhaseResolvesToItsCanonicalDestination() = runBlocking {
        val expected = mapOf(
            FirstPromisePhase.GoalPending to OnboardingEntryDestination.GoalSelect,
            FirstPromisePhase.UsageAccessPending to OnboardingEntryDestination.UsageAccess,
            FirstPromisePhase.Analyzing to OnboardingEntryDestination.UsageAnalysis,
            FirstPromisePhase.ManualSelectPending to OnboardingEntryDestination.ManualAppSelect,
            FirstPromisePhase.DraftReady to OnboardingEntryDestination.PromiseProposal,
            FirstPromisePhase.AccessibilityPending to OnboardingEntryDestination.PromiseAccessibility,
            FirstPromisePhase.NotificationPending to OnboardingEntryDestination.PromiseNotification,
            FirstPromisePhase.PersistFailed to OnboardingEntryDestination.PromiseResult,
            FirstPromisePhase.SchedulePermissionRequired to OnboardingEntryDestination.PromiseResult,
            FirstPromisePhase.ResultEnabled to OnboardingEntryDestination.PromiseResult,
            FirstPromisePhase.ResultDisabled to OnboardingEntryDestination.PromiseResult,
            FirstPromisePhase.CompletedEnabled to OnboardingEntryDestination.Home,
            FirstPromisePhase.CompletedDisabled to OnboardingEntryDestination.Home,
        )

        expected.forEach { (phase, destination) ->
            val state = assigned(OnboardingVariant.PromiseCoachV1).copy(
                phase = phase,
                routineId = if (phase.hasMapping()) 42L else null,
                scheduleState = if (phase.hasMapping()) phase.scheduleState() else null,
            )
            assertEquals(
                "phase=$phase",
                OnboardingEntrySideEffect.Navigate(destination),
                viewModel(state = state).resolveEntry(),
            )
        }

        assertEquals(
            OnboardingEntrySideEffect.WaitForPersistence,
            viewModel(
                state = assigned(OnboardingVariant.PromiseCoachV1).copy(phase = FirstPromisePhase.Persisting),
            ).resolveEntry(),
        )
    }

    @Test
    fun existingTreatmentWithReadableControlAppliesEmergencyRecovery() = runBlocking {
        val exposedTreatment = assigned(OnboardingVariant.PromiseCoachV1).copy(
            phase = FirstPromisePhase.DraftReady,
            trackedMilestones = setOf(FirstPromiseMilestone.Exposure),
        )
        val dataStore = stateDataStore(exposedTreatment)
        val effect = viewModel(
            dataStore = dataStore,
            resolution = readableControl(),
        ).resolveEntry()

        assertEquals(OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.ManualAppSelect), effect)
        assertEquals(OnboardingVariant.PromiseCoachV1, FirstPromiseDraftStore(dataStore).readState().assignment)
        assertEquals(FirstPromisePhase.ManualSelectPending, FirstPromiseDraftStore(dataStore).readState().phase)
    }

    @Test
    fun readableControlResolutionAssignsFreshUsersToControl() = runBlocking {
        val unassignedStore = stateDataStore(FirstPromiseOnboardingState())

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro),
            viewModel(dataStore = unassignedStore, resolution = readableControl()).resolveEntry(),
        )
        assertEquals(OnboardingVariant.Control, FirstPromiseDraftStore(unassignedStore).readState().assignment)
    }

    @Test
    fun emergencySwitchAppliesEveryPhaseSpecificRecoveryBeforeRouting() = runBlocking {
        val manualFallbackPhases = setOf(
            FirstPromisePhase.GoalPending,
            FirstPromisePhase.UsageAccessPending,
            FirstPromisePhase.Analyzing,
            FirstPromisePhase.DraftReady,
            FirstPromisePhase.AccessibilityPending,
            FirstPromisePhase.NotificationPending,
            FirstPromisePhase.PersistFailed,
        )
        FirstPromisePhase.entries.forEach { phase ->
            val mapped = phase.hasMapping()
            val initial = assigned(OnboardingVariant.PromiseCoachV1).copy(
                phase = phase,
                routineId = if (mapped) 42L else null,
                scheduleState = if (mapped) phase.scheduleState() else null,
            )
            val dataStore = stateDataStore(initial)
            val effect = viewModel(
                dataStore = dataStore,
                resolution = readableControl(),
            ).resolveEntry()
            val recovered = FirstPromiseDraftStore(dataStore).readState()

            when {
                phase in manualFallbackPhases -> {
                    assertEquals("phase=$phase", FirstPromisePhase.ManualSelectPending, recovered.phase)
                    assertEquals(
                        "phase=$phase",
                        OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.ManualAppSelect),
                        effect,
                    )
                }
                phase == FirstPromisePhase.Persisting ->
                    assertEquals("phase=$phase", OnboardingEntrySideEffect.WaitForPersistence, effect)
                phase == FirstPromisePhase.ManualSelectPending ->
                    assertEquals(
                        "phase=$phase",
                        OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.ManualAppSelect),
                        effect,
                    )
                phase in setOf(FirstPromisePhase.CompletedEnabled, FirstPromisePhase.CompletedDisabled) -> {
                    assertEquals("phase=$phase", phase, recovered.phase)
                    assertEquals("phase=$phase", OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Home), effect)
                    assertTrue("phase=$phase", recovered.futureAnalysisDisabled)
                }
                else -> {
                    assertEquals("phase=$phase", phase, recovered.phase)
                    assertEquals(
                        "phase=$phase",
                        OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.PromiseResult),
                        effect,
                    )
                    assertTrue("phase=$phase", recovered.futureAnalysisDisabled)
                }
            }
        }
    }

    @Test
    fun emergencyRecoveryNeverReturnsAnExistingRoutineMappingToManualSelection() = runBlocking {
        val mappedPersistFailure = assigned(OnboardingVariant.PromiseCoachV1).copy(
            phase = FirstPromisePhase.PersistFailed,
            routineId = 42L,
            scheduleState = FirstPromiseScheduleState.Enabled,
        )
        val dataStore = stateDataStore(mappedPersistFailure)

        val effect = viewModel(dataStore = dataStore, resolution = readableControl()).resolveEntry()

        assertEquals(OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.PromiseResult), effect)
        assertEquals(FirstPromisePhase.ResultEnabled, FirstPromiseDraftStore(dataStore).readState().phase)
    }

    @Test
    fun exposureMilestoneChangesOnlyOnceForTheAssignedVisibleVariant() = runBlocking {
        val dataStore = stateDataStore(assigned(OnboardingVariant.Control))
        val store = FirstPromiseDraftStore(dataStore)

        assertFalse(store.markExposedIfNeeded(OnboardingVariant.PromiseCoachV1))
        assertTrue(store.markExposedIfNeeded(OnboardingVariant.Control))
        assertFalse(store.markExposedIfNeeded(OnboardingVariant.Control))
    }

    private fun viewModel(
        state: FirstPromiseOnboardingState = FirstPromiseOnboardingState(),
        resolution: OnboardingExperimentResolution = OnboardingExperimentResolution(),
        dataStore: DataStore<Preferences> = stateDataStore(state),
        experimentConfig: CountingExperimentConfig = CountingExperimentConfig(resolution),
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher? = null,
    ) = OnboardingEntryViewModel(
        draftStore = FirstPromiseDraftStore(dataStore),
        experimentConfig = experimentConfig,
        onboardingAnalyticsDispatcher = onboardingAnalyticsDispatcher,
    )

    private fun assigned(variant: OnboardingVariant) = FirstPromiseOnboardingState(
        assignment = variant,
        assignmentVersion = OnboardingAssignmentVersion.V1,
    )

    private fun stateDataStore(state: FirstPromiseOnboardingState) = FakeDataStore(
        mutablePreferencesOf(
            PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
        ),
    )

    private fun readableTreatment() = OnboardingExperimentResolution(
        variant = OnboardingVariant.PromiseCoachV1,
        remoteReadable = true,
    )

    private fun readableControl() = OnboardingExperimentResolution(
        variant = OnboardingVariant.Control,
        remoteReadable = true,
    )

    private fun FirstPromisePhase.hasMapping() = this in setOf(
        FirstPromisePhase.SchedulePermissionRequired,
        FirstPromisePhase.ResultEnabled,
        FirstPromisePhase.ResultDisabled,
        FirstPromisePhase.CompletedEnabled,
        FirstPromisePhase.CompletedDisabled,
    )

    private fun FirstPromisePhase.scheduleState() = when (this) {
        FirstPromisePhase.ResultEnabled,
        FirstPromisePhase.CompletedEnabled,
        -> FirstPromiseScheduleState.Enabled
        else -> FirstPromiseScheduleState.DisabledExactAlarmMissing
    }
}

private class CountingDataStore(initial: Preferences) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    var updateCount: Int = 0
        private set

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences,
    ): Preferences {
        updateCount += 1
        return transform(state.value).also { state.value = it }
    }

    fun snapshot(): Preferences = state.value
}

private class CountingExperimentConfig(
    private val resolution: OnboardingExperimentResolution,
) : OnboardingExperimentConfig {
    var resolveCount: Int = 0
        private set

    override suspend fun resolve(): OnboardingExperimentResolution {
        resolveCount += 1
        return resolution
    }
}

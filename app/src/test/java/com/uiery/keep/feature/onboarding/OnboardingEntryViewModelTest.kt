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
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentSnapshot
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
        val viewModel = viewModel(
            dataStore = dataStore,
            snapshot = OnboardingExperimentSnapshot(
                treatmentPercent = 100,
                newAssignmentEnabled = true,
                remoteReadable = true,
            ),
        )

        val effect = async { viewModel.container.sideEffectFlow.first() }
        viewModel.resolve()
        val emittedEffect = withTimeout(1_000L) { effect.await() }

        assertEquals(OnboardingEntrySideEffect.StateCorrupted, emittedEffect)
        assertFalse(emittedEffect is OnboardingEntrySideEffect.Navigate)
        assertEquals(0, dataStore.updateCount)
        assertEquals(malformed, dataStore.snapshot()[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])
        assertEquals(
            FirstPromiseStateReadResult.Corrupted,
            FirstPromiseDraftStore(dataStore).observeStateResult().first(),
        )
    }

    @Test
    fun absentPersistedStateIsAValidFreshInstallAndStillAssigns() = runBlocking {
        val dataStore = FakeDataStore()

        val effect = viewModel(
            dataStore = dataStore,
            snapshot = OnboardingExperimentSnapshot(
                treatmentPercent = 100,
                newAssignmentEnabled = true,
                remoteReadable = true,
            ),
        ).resolveEntry()

        assertEquals(OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.GoalSelect), effect)
        assertEquals(OnboardingVariant.PromiseCoachV1, FirstPromiseDraftStore(dataStore).readState().assignment)
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
        val viewModel = viewModel(dataStore = dataStore, snapshot = emergencySnapshot())
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
        viewModel.resolve()
        assertEquals(null, withTimeoutOrNull(100L) { viewModel.container.sideEffectFlow.first() })
    }

    @Test
    fun persistingWaitsWithoutNavigationUntilFailureThenRoutesManualOnce() = runBlocking {
        val dataStore = stateDataStore(
            assigned(OnboardingVariant.PromiseCoachV1).copy(phase = FirstPromisePhase.Persisting),
        )
        val viewModel = viewModel(dataStore = dataStore, snapshot = emergencySnapshot())
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
        viewModel.resolve()
        assertEquals(null, withTimeoutOrNull(100L) { viewModel.container.sideEffectFlow.first() })
    }

    @Test
    fun controlKeepsTheExistingIntroWhileNewTreatmentStartsAtGoalSelect() = runBlocking {
        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro),
            viewModel(state = assigned(OnboardingVariant.Control)).resolveEntry(),
        )
        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.GoalSelect),
            viewModel(state = assigned(OnboardingVariant.PromiseCoachV1)).resolveEntry(),
        )
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
            FirstPromisePhase.PersistFailed to OnboardingEntryDestination.PromiseProposal,
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
    fun stickyAssignmentIgnoresLaterRolloutAndGeneralKillSwitchChanges() = runBlocking {
        val exposedTreatment = assigned(OnboardingVariant.PromiseCoachV1).copy(
            trackedMilestones = setOf(FirstPromiseMilestone.Exposure),
        )
        val dataStore = stateDataStore(exposedTreatment)
        val effect = viewModel(
            dataStore = dataStore,
            snapshot = OnboardingExperimentSnapshot(
                treatmentPercent = 0,
                newAssignmentEnabled = false,
                remoteReadable = true,
            ),
        ).resolveEntry()

        assertEquals(OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.GoalSelect), effect)
        assertEquals(OnboardingVariant.PromiseCoachV1, FirstPromiseDraftStore(dataStore).readState().assignment)
    }

    @Test
    fun generalKillSwitchOnlyControlsPreviouslyUnassignedUsers() = runBlocking {
        val disabledSnapshot = OnboardingExperimentSnapshot(
            treatmentPercent = 100,
            newAssignmentEnabled = false,
            remoteReadable = true,
        )
        val unassignedStore = stateDataStore(FirstPromiseOnboardingState())

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro),
            viewModel(dataStore = unassignedStore, snapshot = disabledSnapshot).resolveEntry(),
        )
        assertEquals(OnboardingVariant.Control, FirstPromiseDraftStore(unassignedStore).readState().assignment)

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.GoalSelect),
            viewModel(state = assigned(OnboardingVariant.PromiseCoachV1), snapshot = disabledSnapshot).resolveEntry(),
        )
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
                snapshot = emergencySnapshot(),
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

        val effect = viewModel(dataStore = dataStore, snapshot = emergencySnapshot()).resolveEntry()

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
        snapshot: OnboardingExperimentSnapshot = OnboardingExperimentSnapshot(),
        dataStore: DataStore<Preferences> = stateDataStore(state),
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher? = null,
    ) = OnboardingEntryViewModel(
        draftStore = FirstPromiseDraftStore(dataStore),
        experimentConfig = object : OnboardingExperimentConfig {
            override fun snapshot() = snapshot
        },
        bucketProvider = { 0 },
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

    private fun emergencySnapshot() = OnboardingExperimentSnapshot(
        treatmentPercent = 100,
        newAssignmentEnabled = true,
        emergencyDisabled = true,
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

package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseEmergencyAction
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromisePersistenceResolution
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.PendingSystemAction
import com.uiery.keep.domain.firstpromise.RecommendationReasonRef
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.domain.firstpromise.UsagePermissionAttempt
import com.uiery.keep.domain.firstpromise.UsagePermissionLaunchState
import com.uiery.keep.domain.firstpromise.UsagePermissionOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPromiseDraftStoreTest {

    @Test
    fun firstAssignmentWinsAndSelfOrRejectedTransitionsDoNotWrite() = runBlocking {
        val dataStore = FirstPromiseFakeDataStore()
        val store = FirstPromiseDraftStore(dataStore)

        val assigned = store.assignIfAbsent(OnboardingVariant.PromiseCoachV1, OnboardingAssignmentVersion.V1)
        val writeCountAfterAssignment = dataStore.editCount
        val repeated = store.assignIfAbsent(OnboardingVariant.Control, OnboardingAssignmentVersion.V1)

        assertEquals(OnboardingVariant.PromiseCoachV1, assigned.assignment)
        assertEquals(OnboardingVariant.PromiseCoachV1, repeated.assignment)
        assertEquals(writeCountAfterAssignment, dataStore.editCount)

        val rejected = store.transitionTo(FirstPromisePhase.ResultEnabled)
        assertEquals(FirstPromiseStateMutation.Rejected, rejected)
        assertEquals(writeCountAfterAssignment, dataStore.editCount)

        val selfTransitionDataStore = FirstPromiseFakeDataStore(
            statePreferences(FirstPromiseOnboardingState(phase = FirstPromisePhase.ManualSelectPending)),
        )
        val selfTransition = FirstPromiseDraftStore(selfTransitionDataStore)
            .transitionTo(FirstPromisePhase.ManualSelectPending)
        assertEquals(FirstPromiseStateMutation.NoOp, selfTransition)
        assertEquals(0, selfTransitionDataStore.editCount)
    }

    @Test
    fun typedDraftReasonPendingActionAndPermissionAttemptSurviveStoreRecreation() = runBlocking {
        val expected = completeDraftState()
        val dataStore = FirstPromiseFakeDataStore(statePreferences(expected))

        val recreatedStore = FirstPromiseDraftStore(dataStore)

        assertEquals(expected, recreatedStore.readState())
        val json = dataStore.snapshot()[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE].orEmpty()
        assertFalse(json.contains("averageDailyMinutes", ignoreCase = true))
        assertFalse(json.contains("rawIntervals", ignoreCase = true))
        assertFalse(json.contains("lastUsedEpoch", ignoreCase = true))
        assertFalse(json.contains("totalForeground", ignoreCase = true))
    }

    @Test
    fun openedAttemptAfterRecreationBecomesUnresolvedWithoutATerminalOutcome() = runBlocking {
        val opened = completeDraftState().copy(
            usagePermissionAttempt = UsagePermissionAttempt(5L, UsagePermissionLaunchState.Opened),
        )
        val store = FirstPromiseDraftStore(FirstPromiseFakeDataStore(statePreferences(opened)))

        val emittedOutcome = store.reconcileUsagePermissionAfterRecreation(permissionGranted = false)

        assertNull(emittedOutcome)
        assertEquals(
            UsagePermissionAttempt(5L, UsagePermissionLaunchState.UnresolvedAfterRecreation),
            store.readState().usagePermissionAttempt,
        )
    }

    @Test
    fun launchFailureFalseResumeAndManualChoiceProduceExactTypedTerminalOutcomes() = runBlocking {
        val store = FirstPromiseDraftStore(FirstPromiseFakeDataStore())

        assertTrue(store.beginUsagePermissionAttempt(1L))
        assertTrue(store.recordUsagePermissionLaunchFailed(1L))
        assertEquals(
            UsagePermissionAttempt(1L, UsagePermissionLaunchState.LaunchFailed, UsagePermissionOutcome.Unknown),
            store.readState().usagePermissionAttempt,
        )

        assertTrue(store.beginUsagePermissionAttempt(2L))
        assertTrue(store.recordUsagePermissionOpened(2L))
        assertEquals(
            UsagePermissionOutcome.Denied,
            store.recordUsagePermissionResume(2L, permissionGranted = false, sameProcess = true),
        )

        assertTrue(store.beginManualUsagePermissionAttempt(3L))
        assertEquals(
            UsagePermissionAttempt(3L, UsagePermissionLaunchState.NotLaunched, UsagePermissionOutcome.Skipped),
            store.readState().usagePermissionAttempt,
        )
    }

    @Test
    fun attemptAcceptsOneTerminalResultButLaterAttemptMayBeGrantedAndStaleCallbacksAreIgnored() = runBlocking {
        val store = FirstPromiseDraftStore(FirstPromiseFakeDataStore())

        assertTrue(store.beginUsagePermissionAttempt(10L))
        assertTrue(store.recordUsagePermissionOpened(10L))
        assertEquals(
            UsagePermissionOutcome.Denied,
            store.recordUsagePermissionResume(10L, permissionGranted = false, sameProcess = true),
        )
        assertNull(store.recordUsagePermissionResume(10L, permissionGranted = true, sameProcess = true))

        assertTrue(store.beginUsagePermissionAttempt(11L))
        assertNull(store.recordUsagePermissionResume(10L, permissionGranted = true, sameProcess = true))
        assertTrue(store.recordUsagePermissionOpened(11L))
        assertEquals(
            UsagePermissionOutcome.Granted,
            store.recordUsagePermissionResume(11L, permissionGranted = true, sameProcess = true),
        )
        assertEquals(UsagePermissionOutcome.Granted, store.readState().usagePermissionAttempt?.terminalOutcome)
    }

    @Test
    fun completionClearsTemporaryStateAndRetainsExperimentMappingAndFinalSchedule() = runBlocking {
        val initial = completeDraftState().copy(
            phase = FirstPromisePhase.ResultEnabled,
            routineId = 42L,
            scheduleState = FirstPromiseScheduleState.Enabled,
        )
        val dataStore = FirstPromiseFakeDataStore(statePreferences(initial))
        val store = FirstPromiseDraftStore(dataStore)

        val mutation = store.transitionTo(FirstPromisePhase.CompletedEnabled)
        val completed = store.readState()

        assertTrue(mutation is FirstPromiseStateMutation.Changed)
        assertEquals(OnboardingVariant.PromiseCoachV1, completed.assignment)
        assertEquals(OnboardingAssignmentVersion.V1, completed.assignmentVersion)
        assertEquals(setOf(FirstPromiseMilestone.Exposure, FirstPromiseMilestone.AppSelection), completed.trackedMilestones)
        assertEquals(42L, completed.routineId)
        assertEquals(FirstPromiseScheduleState.Enabled, completed.scheduleState)
        assertNull(completed.draft)
        assertNull(completed.recommendationReasonRef)
        assertNull(completed.pendingSystemAction)
        assertEquals(1, dataStore.editCount)
    }

    @Test
    fun emergencyAndPersistenceResolutionEachUseOneAtomicEdit() = runBlocking {
        val draftReadyDataStore = FirstPromiseFakeDataStore(
            statePreferences(completeDraftState().copy(phase = FirstPromisePhase.DraftReady)),
        )
        val draftReadyStore = FirstPromiseDraftStore(draftReadyDataStore)

        val emergency = draftReadyStore.applyEmergency()

        assertEquals(FirstPromiseEmergencyAction.NavigateManualSelect, emergency.action)
        assertEquals(1, draftReadyDataStore.editCount)
        assertEquals(FirstPromisePhase.ManualSelectPending, draftReadyStore.readState().phase)

        val persistingDataStore = FirstPromiseFakeDataStore(
            statePreferences(completeDraftState().copy(phase = FirstPromisePhase.Persisting)),
        )
        val persistingStore = FirstPromiseDraftStore(persistingDataStore)

        val waiting = persistingStore.applyEmergency()
        val resolved = persistingStore.resolveEmergencyPersistence(
            FirstPromisePersistenceResolution.Succeeded(45L, FirstPromiseScheduleState.Enabled),
        )

        assertEquals(FirstPromiseEmergencyAction.WaitForPersistence, waiting.action)
        assertEquals(FirstPromisePhase.ResultEnabled, resolved.state.phase)
        assertEquals(1, persistingDataStore.editCount)
    }

    private fun completeDraftState() = FirstPromiseOnboardingState(
        assignment = OnboardingVariant.PromiseCoachV1,
        assignmentVersion = OnboardingAssignmentVersion.V1,
        trackedMilestones = setOf(FirstPromiseMilestone.Exposure, FirstPromiseMilestone.AppSelection),
        phase = FirstPromisePhase.AccessibilityPending,
        path = FirstPromisePath.Personalized,
        goal = FirstPromiseGoal.Sleep,
        draft = FirstPromiseDraft(
            draftId = "local-draft",
            goal = FirstPromiseGoal.Sleep,
            packageName = "com.example.video",
            appLabel = "Video",
            startMinutes = 22 * 60,
            repeatDays = setOf(1, 3, 5),
            source = FirstPromiseSource.Personalized,
        ),
        recommendationReasonRef = RecommendationReasonRef(
            patternType = UsagePatternType.Night,
            usageCoverageDays = 7,
            eventCoverageDays = 6,
            isGoalDefault = false,
            selectedStartMinutes = 22 * 60,
        ),
        pendingSystemAction = PendingSystemAction.Accessibility,
        usagePermissionAttempt = UsagePermissionAttempt(
            id = 5L,
            launchState = UsagePermissionLaunchState.Opened,
        ),
        analysisAttemptId = 8L,
    )

    private fun statePreferences(state: FirstPromiseOnboardingState): Preferences = mutablePreferencesOf(
        PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
    )
}

private class FirstPromiseFakeDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    var editCount: Int = 0
        private set

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        editCount += 1
        val next = transform(state.value)
        state.value = next
        return next
    }

    fun snapshot(): Preferences = state.value
}

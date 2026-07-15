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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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

        val rejected = store.completeOnboarding()
        assertEquals(FirstPromiseStateMutation.Rejected, rejected)
        assertEquals(writeCountAfterAssignment, dataStore.editCount)

        val selfTransitionDataStore = FirstPromiseFakeDataStore(
            statePreferences(FirstPromiseOnboardingState(phase = FirstPromisePhase.ManualSelectPending)),
        )
        val selfTransition = FirstPromiseDraftStore(selfTransitionDataStore)
            .chooseManualSetup()
        assertEquals(FirstPromiseStateMutation.NoOp, selfTransition)
        assertEquals(0, selfTransitionDataStore.editCount)
    }

    @Test
    fun publicCommandsCannotManufactureInvariantBearingPhases() = runBlocking {
        val publicMethodNames = FirstPromiseDraftStore::class.java.methods.mapTo(mutableSetOf()) { it.name }
        assertFalse("transitionTo" in publicMethodNames)
        assertFalse("startAnalysis" in publicMethodNames)

        val dataStore = FirstPromiseFakeDataStore()
        val store = FirstPromiseDraftStore(dataStore)

        assertEquals(FirstPromiseStateMutation.Rejected, store.requestAccessibility())
        assertEquals(FirstPromiseStateMutation.Rejected, store.returnToDraft())
        assertEquals(FirstPromiseStateMutation.Rejected, store.requestNotification())
        assertEquals(FirstPromiseStateMutation.Rejected, store.beginPersistence())
        assertEquals(FirstPromiseStateMutation.Rejected, store.markPersistenceFailed())
        assertEquals(FirstPromiseStateMutation.Rejected, store.completeOnboarding())
        assertEquals(0, dataStore.editCount)
    }

    @Test
    fun publicMutationApisPersistCompleteTypedStateAcrossStoreRecreation() = runBlocking {
        val dataStore = FirstPromiseFakeDataStore()
        val store = FirstPromiseDraftStore(dataStore)
        val firstDraft = draft(draftId = "local-draft", startMinutes = 22 * 60)
        val firstReason = reason(startMinutes = 22 * 60)
        val editedDraft = draft(draftId = "local-draft", startMinutes = 21 * 60)
        val editedReason = reason(startMinutes = 21 * 60)

        store.assignIfAbsent(OnboardingVariant.PromiseCoachV1, OnboardingAssignmentVersion.V1)
        assertTrue(store.markMilestone(FirstPromiseMilestone.Exposure) is FirstPromiseStateMutation.Changed)
        assertEquals(FirstPromiseStateMutation.NoOp, store.markMilestone(FirstPromiseMilestone.Exposure))
        assertTrue(
            store.selectGoal(FirstPromiseGoal.Sleep, FirstPromisePath.Personalized) is
                FirstPromiseStateMutation.Changed,
        )
        assertTrue(store.advanceToUsageAccess() is FirstPromiseStateMutation.Changed)
        assertTrue(store.setPendingSystemAction(PendingSystemAction.UsageAccess) is FirstPromiseStateMutation.Changed)
        assertTrue(store.beginUsagePermissionAttempt(5L))
        assertTrue(store.recordUsagePermissionOpened(5L))
        assertTrue(store.clearPendingSystemAction() is FirstPromiseStateMutation.Changed)
        assertTrue(store.beginUsageAnalysis(8L) is FirstPromiseStateMutation.Changed)
        assertTrue(store.completeAnalysis(8L, firstDraft, firstReason))
        assertTrue(store.storeDraft(editedDraft, editedReason) is FirstPromiseStateMutation.Changed)
        assertTrue(store.setPendingSystemAction(PendingSystemAction.Accessibility) is FirstPromiseStateMutation.Changed)

        val recreatedStore = FirstPromiseDraftStore(dataStore)
        val expected = FirstPromiseOnboardingState(
            assignment = OnboardingVariant.PromiseCoachV1,
            assignmentVersion = OnboardingAssignmentVersion.V1,
            trackedMilestones = setOf(FirstPromiseMilestone.Exposure),
            phase = FirstPromisePhase.DraftReady,
            path = FirstPromisePath.Personalized,
            goal = FirstPromiseGoal.Sleep,
            draft = editedDraft,
            recommendationReasonRef = editedReason,
            pendingSystemAction = PendingSystemAction.Accessibility,
            usagePermissionAttempt = UsagePermissionAttempt(5L, UsagePermissionLaunchState.Opened),
            analysisAttemptId = 8L,
        )

        assertEquals(expected, recreatedStore.readState())
        assertEquals(12, dataStore.editCount)
        val json = dataStore.snapshot()[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE].orEmpty()
        val stateJson = Json.parseToJsonElement(json).jsonObject
        assertEquals(
            setOf(
                "assignment",
                "assignmentVersion",
                "trackedMilestones",
                "phase",
                "goal",
                "draft",
                "recommendationReasonRef",
                "pendingSystemAction",
                "usagePermissionAttempt",
                "analysisAttemptId",
            ),
            stateJson.keys,
        )
        assertEquals(
            setOf("draftId", "goal", "packageName", "appLabel", "startMinutes", "repeatDays", "source"),
            stateJson.getValue("draft").jsonObject.keys,
        )
        assertEquals(
            setOf(
                "patternType",
                "usageCoverageDays",
                "eventCoverageDays",
                "isGoalDefault",
                "selectedStartMinutes",
            ),
            stateJson.getValue("recommendationReasonRef").jsonObject.keys,
        )
        assertEquals(
            setOf("id", "launchState"),
            stateJson.getValue("usagePermissionAttempt").jsonObject.keys,
        )
    }

    @Test
    fun staleAnalysisCompletionDoesNotWriteOrExposeSuccess() = runBlocking {
        val initial = completeDraftState().copy(
            phase = FirstPromisePhase.Analyzing,
            analysisAttemptId = 8L,
            draft = null,
            recommendationReasonRef = null,
        )
        val dataStore = FirstPromiseFakeDataStore(statePreferences(initial))
        val store = FirstPromiseDraftStore(dataStore)

        val staleAccepted = store.completeAnalysis(7L, draft("late", 20 * 60), reason(20 * 60))

        assertFalse(staleAccepted)
        assertEquals(0, dataStore.editCount)
        assertEquals(initial, store.readState())

        assertTrue(store.completeAnalysis(8L, draft("current", 21 * 60), reason(21 * 60)))
        assertEquals(1, dataStore.editCount)
        assertEquals(FirstPromisePhase.DraftReady, store.readState().phase)
    }

    @Test
    fun staleAnalysisTimeoutCannotTerminateCurrentAttemptAndCurrentFailureIsAtomic() = runBlocking {
        val initial = completeDraftState().copy(
            phase = FirstPromisePhase.Analyzing,
            pendingSystemAction = PendingSystemAction.UsageAccess,
            analysisAttemptId = 9L,
        )
        val dataStore = FirstPromiseFakeDataStore(statePreferences(initial))
        val store = FirstPromiseDraftStore(dataStore)

        assertFalse(store.failAnalysis(8L))
        assertEquals(0, dataStore.editCount)
        assertEquals(9L, store.readState().analysisAttemptId)
        assertEquals(FirstPromisePhase.Analyzing, store.readState().phase)

        assertTrue(store.failAnalysis(9L))

        val failed = store.readState()
        assertEquals(1, dataStore.editCount)
        assertEquals(FirstPromisePhase.ManualSelectPending, failed.phase)
        assertEquals(FirstPromisePath.Manual, failed.path)
        assertNull(failed.draft)
        assertNull(failed.recommendationReasonRef)
        assertNull(failed.pendingSystemAction)
        assertNull(failed.analysisAttemptId)
        assertFalse(failed.futureAnalysisDisabled)
        assertFalse(store.failAnalysis(9L))
        assertEquals(1, dataStore.editCount)
    }

    @Test
    fun normalPersistenceMappingIsAtomicIdempotentAndRejectsConflicts() = runBlocking {
        val enabledDataStore = FirstPromiseFakeDataStore(
            statePreferences(completeDraftState().copy(phase = FirstPromisePhase.Persisting)),
        )
        val enabledStore = FirstPromiseDraftStore(enabledDataStore)

        assertEquals(
            FirstPromiseStateMutation.Rejected,
            enabledStore.recordPersistenceMapping(0L, FirstPromiseScheduleState.Enabled),
        )
        assertEquals(0, enabledDataStore.editCount)
        val saved = enabledStore.recordPersistenceMapping(41L, FirstPromiseScheduleState.Enabled)
        val repeated = enabledStore.recordPersistenceMapping(41L, FirstPromiseScheduleState.Enabled)
        val conflicting = enabledStore.recordPersistenceMapping(42L, FirstPromiseScheduleState.Enabled)

        assertTrue(saved is FirstPromiseStateMutation.Changed)
        assertEquals(FirstPromisePhase.ResultEnabled, enabledStore.readState().phase)
        assertEquals(41L, enabledStore.readState().routineId)
        assertEquals(FirstPromiseStateMutation.NoOp, repeated)
        assertEquals(FirstPromiseStateMutation.Rejected, conflicting)
        assertEquals(1, enabledDataStore.editCount)

        val disabledDataStore = FirstPromiseFakeDataStore(
            statePreferences(completeDraftState().copy(phase = FirstPromisePhase.Persisting)),
        )
        val disabledStore = FirstPromiseDraftStore(disabledDataStore)

        assertTrue(
            disabledStore.recordPersistenceMapping(
                43L,
                FirstPromiseScheduleState.DisabledExactAlarmMissing,
            ) is FirstPromiseStateMutation.Changed,
        )
        assertEquals(FirstPromisePhase.SchedulePermissionRequired, disabledStore.readState().phase)
        assertEquals(43L, disabledStore.readState().routineId)
        assertEquals(1, disabledDataStore.editCount)
    }

    @Test
    fun concurrentIdenticalAndConflictingMappingsPerformOnlyTheAcceptedEdit() = runBlocking {
        val initial = statePreferences(completeDraftState().copy(phase = FirstPromisePhase.Persisting))
        val identicalDataStore = FirstPromiseFakeDataStore(initial, synchronizeInitialReads = 2)
        val identicalStore = FirstPromiseDraftStore(identicalDataStore)

        val identicalResults = listOf(
            async(Dispatchers.Default) {
                identicalStore.recordPersistenceMapping(41L, FirstPromiseScheduleState.Enabled)
            },
            async(Dispatchers.Default) {
                identicalStore.recordPersistenceMapping(41L, FirstPromiseScheduleState.Enabled)
            },
        ).awaitAll()

        assertEquals(1, identicalResults.count { it is FirstPromiseStateMutation.Changed })
        assertEquals(1, identicalResults.count { it is FirstPromiseStateMutation.NoOp })
        assertEquals(1, identicalDataStore.editCount)

        val conflictingDataStore = FirstPromiseFakeDataStore(initial, synchronizeInitialReads = 2)
        val conflictingStore = FirstPromiseDraftStore(conflictingDataStore)
        val conflictingResults = listOf(
            async(Dispatchers.Default) {
                conflictingStore.recordPersistenceMapping(42L, FirstPromiseScheduleState.Enabled)
            },
            async(Dispatchers.Default) {
                conflictingStore.recordPersistenceMapping(43L, FirstPromiseScheduleState.Enabled)
            },
        ).awaitAll()

        assertEquals(1, conflictingResults.count { it is FirstPromiseStateMutation.Changed })
        assertEquals(1, conflictingResults.count { it is FirstPromiseStateMutation.Rejected })
        assertEquals(1, conflictingDataStore.editCount)
    }

    @Test
    fun concurrentConflictingAndStaleAnalysisCallbacksPerformOnlyTheAcceptedEdit() = runBlocking {
        val initial = completeDraftState().copy(
            phase = FirstPromisePhase.Analyzing,
            analysisAttemptId = 9L,
            draft = null,
            recommendationReasonRef = null,
        )
        val dataStore = FirstPromiseFakeDataStore(
            initial = statePreferences(initial),
            synchronizeInitialReads = 3,
        )
        val store = FirstPromiseDraftStore(dataStore)

        val results = listOf(
            async(Dispatchers.Default) {
                store.completeAnalysis(9L, draft("current", 21 * 60), reason(21 * 60))
            },
            async(Dispatchers.Default) { store.failAnalysis(9L) },
            async(Dispatchers.Default) {
                store.completeAnalysis(8L, draft("stale", 20 * 60), reason(20 * 60))
            },
        ).awaitAll()

        assertEquals(1, results.count { it })
        assertEquals(1, dataStore.editCount)
        assertTrue(store.readState().phase in setOf(FirstPromisePhase.DraftReady, FirstPromisePhase.ManualSelectPending))
    }

    @Test
    fun sameRoutineScheduleResolutionUpdatesFinalStateAtomicallyAndRejectsOtherMappings() = runBlocking {
        val disabledStates = listOf(
            FirstPromiseScheduleState.DisabledExactAlarmMissing,
            FirstPromiseScheduleState.DisabledUserChoice,
            FirstPromiseScheduleState.DisabledUnknown,
        )

        disabledStates.forEachIndexed { index, initialSchedule ->
            val dataStore = FirstPromiseFakeDataStore(
                statePreferences(
                    completeDraftState().copy(
                        phase = FirstPromisePhase.SchedulePermissionRequired,
                        routineId = 100L + index,
                        scheduleState = initialSchedule,
                        pendingSystemAction = PendingSystemAction.ExactAlarm,
                    ),
                ),
            )
            val store = FirstPromiseDraftStore(dataStore)
            val routineId = 100L + index

            val enabled = store.resolveScheduleState(routineId, FirstPromiseScheduleState.Enabled)
            val repeated = store.resolveScheduleState(routineId, FirstPromiseScheduleState.Enabled)
            val conflicting = store.resolveScheduleState(routineId + 100L, FirstPromiseScheduleState.Enabled)

            assertTrue(enabled is FirstPromiseStateMutation.Changed)
            assertEquals(FirstPromisePhase.ResultEnabled, store.readState().phase)
            assertEquals(FirstPromiseScheduleState.Enabled, store.readState().scheduleState)
            assertNull(store.readState().pendingSystemAction)
            assertEquals(FirstPromiseStateMutation.NoOp, repeated)
            assertEquals(FirstPromiseStateMutation.Rejected, conflicting)
            assertEquals(1, dataStore.editCount)
        }

        disabledStates.forEachIndexed { index, finalSchedule ->
            val routineId = 200L + index
            val dataStore = FirstPromiseFakeDataStore(
                statePreferences(
                    completeDraftState().copy(
                        phase = FirstPromisePhase.SchedulePermissionRequired,
                        routineId = routineId,
                        scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
                    ),
                ),
            )
            val store = FirstPromiseDraftStore(dataStore)

            val disabled = store.resolveScheduleState(routineId, finalSchedule)

            assertTrue(disabled is FirstPromiseStateMutation.Changed)
            assertEquals(FirstPromisePhase.ResultDisabled, store.readState().phase)
            assertEquals(finalSchedule, store.readState().scheduleState)
            assertEquals(1, dataStore.editCount)
        }
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

        val mutation = store.completeOnboarding()
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
        assertNull(completed.usagePermissionAttempt)
        assertNull(completed.analysisAttemptId)

        val repeated = store.completeOnboarding()
        assertEquals(FirstPromiseStateMutation.NoOp, repeated)
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

    @Test
    fun recreationRecoveryNormalizesACompleteEarlyMappingInOneEdit() = runBlocking {
        val mappedManual = completeDraftState().copy(
            phase = FirstPromisePhase.ManualSelectPending,
            routineId = 45L,
            scheduleState = FirstPromiseScheduleState.Enabled,
        )
        val dataStore = FirstPromiseFakeDataStore(statePreferences(mappedManual))
        val store = FirstPromiseDraftStore(dataStore)

        val mutation = store.recoverAfterRecreation()

        assertTrue(mutation is FirstPromiseStateMutation.Changed)
        assertEquals(FirstPromisePhase.ResultEnabled, store.readState().phase)
        assertEquals(45L, store.readState().routineId)
        assertEquals(1, dataStore.editCount)
    }

    @Test
    fun concurrentDuplicateEmergencyResolutionReturnsTypedRejectionWithoutAnotherEdit() = runBlocking {
        val initial = completeDraftState().copy(phase = FirstPromisePhase.Persisting)
        val dataStore = FirstPromiseFakeDataStore(
            initial = statePreferences(initial),
            synchronizeInitialReads = 2,
        )
        val store = FirstPromiseDraftStore(dataStore)
        val resolution = FirstPromisePersistenceResolution.Succeeded(
            routineId = 45L,
            scheduleState = FirstPromiseScheduleState.Enabled,
        )

        val results = listOf(
            async(Dispatchers.Default) { store.resolveEmergencyPersistence(resolution) },
            async(Dispatchers.Default) { store.resolveEmergencyPersistence(resolution) },
        ).awaitAll()

        assertEquals(1, results.count { it.action == FirstPromiseEmergencyAction.Stay })
        assertEquals(1, results.count { it.action == FirstPromiseEmergencyAction.Rejected })
        assertEquals(1, dataStore.editCount)
        assertEquals(FirstPromisePhase.ResultEnabled, store.readState().phase)
    }

    private fun completeDraftState() = FirstPromiseOnboardingState(
        assignment = OnboardingVariant.PromiseCoachV1,
        assignmentVersion = OnboardingAssignmentVersion.V1,
        trackedMilestones = setOf(FirstPromiseMilestone.Exposure, FirstPromiseMilestone.AppSelection),
        phase = FirstPromisePhase.AccessibilityPending,
        path = FirstPromisePath.Personalized,
        goal = FirstPromiseGoal.Sleep,
        draft = draft(draftId = "local-draft", startMinutes = 22 * 60),
        recommendationReasonRef = reason(startMinutes = 22 * 60),
        pendingSystemAction = PendingSystemAction.Accessibility,
        usagePermissionAttempt = UsagePermissionAttempt(
            id = 5L,
            launchState = UsagePermissionLaunchState.Opened,
        ),
        analysisAttemptId = 8L,
    )

    private fun draft(draftId: String, startMinutes: Int) = FirstPromiseDraft(
        draftId = draftId,
        goal = FirstPromiseGoal.Sleep,
        packageName = "com.example.video",
        appLabel = "Video",
        startMinutes = startMinutes,
        repeatDays = setOf(1, 3, 5),
        source = FirstPromiseSource.Personalized,
    )

    private fun reason(startMinutes: Int) = RecommendationReasonRef(
        patternType = UsagePatternType.Night,
        usageCoverageDays = 7,
        eventCoverageDays = 6,
        isGoalDefault = false,
        selectedStartMinutes = startMinutes,
    )

    private fun statePreferences(state: FirstPromiseOnboardingState): Preferences = mutablePreferencesOf(
        PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
    )
}

private class FirstPromiseFakeDataStore(
    initial: Preferences = emptyPreferences(),
    private val synchronizeInitialReads: Int = 0,
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private val updateMutex = Mutex()
    private val readCount = AtomicInteger()
    private val initialReadBarrier = CompletableDeferred<Unit>()
    var editCount: Int = 0
        private set

    override val data: Flow<Preferences> = if (synchronizeInitialReads == 0) {
        state
    } else {
        flow {
            val snapshot = state.value
            val currentRead = readCount.incrementAndGet()
            if (currentRead <= synchronizeInitialReads) {
                if (currentRead == synchronizeInitialReads) {
                    initialReadBarrier.complete(Unit)
                }
                withTimeoutOrNull(250L) { initialReadBarrier.await() }
            }
            emit(snapshot)
        }
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        updateMutex.withLock {
            editCount += 1
            val next = transform(state.value)
            state.value = next
            next
        }

    fun snapshot(): Preferences = state.value
}

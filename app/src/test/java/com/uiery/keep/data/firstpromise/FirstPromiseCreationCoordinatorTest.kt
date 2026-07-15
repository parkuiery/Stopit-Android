package com.uiery.keep.data.firstpromise

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.RecommendationReasonRef
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.model.RoutineModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FirstPromiseCreationCoordinatorTest {
    private val draft = FirstPromiseDraft(
        draftId = "draft",
        goal = FirstPromiseGoal.Study,
        packageName = "com.example.study",
        appLabel = "Study",
        startMinutes = 23 * 60 + 45,
        repeatDays = setOf(1, 3, 5),
        source = FirstPromiseSource.GoalTemplate,
    )

    @Test
    fun committedEnabledCreationMapsStateDrainsThenTracksFirstLockOnce() = runBlocking {
        val dataStore = persistingDataStore(draft)
        val creator = FakeCreator(successResult())
        val dispatcher = FakeDispatcher(sent = true)
        val analytics = CoordinatorRecordingAnalytics()
        val coordinator = FirstPromiseCreationCoordinator(
            creator,
            dispatcher,
            FirstPromiseDraftStore(dataStore),
            BlockingStateStore(dataStore),
            analytics,
        )

        val first = coordinator.persistCurrentDraft()
        val second = coordinator.persistCurrentDraft()

        assertTrue(first is FirstPromisePersistenceResult.Succeeded)
        assertTrue(second is FirstPromisePersistenceResult.Succeeded)
        assertEquals(1, analytics.firstLockCalls)
        assertEquals(listOf("draft", "draft"), dispatcher.drainedDrafts)
        val state = FirstPromiseDraftStore(dataStore).readState()
        assertEquals(41L, state.routineId)
        assertEquals(FirstPromisePhase.ResultEnabled, state.phase)
        assertEquals("1010100", creator.lastRoutine?.repeatDays)
        assertEquals(0, creator.lastRoutine?.endTime?.hour)
        assertEquals(15, creator.lastRoutine?.endTime?.minute)
    }

    @Test
    fun transactionFailureRetainsDraftAndMovesPersistFailed() = runBlocking {
        val dataStore = persistingDataStore(draft)
        val coordinator = FirstPromiseCreationCoordinator(
            FakeCreator(failure = IllegalStateException("db")),
            FakeDispatcher(sent = false),
            FirstPromiseDraftStore(dataStore),
            BlockingStateStore(dataStore),
            CoordinatorRecordingAnalytics(),
        )

        val result = coordinator.persistCurrentDraft()

        assertTrue(result is FirstPromisePersistenceResult.Failed)
        val state = FirstPromiseDraftStore(dataStore).readState()
        assertEquals(FirstPromisePhase.PersistFailed, state.phase)
        assertEquals(draft, state.draft)
    }

    @Test
    fun postCommitMappingWriteFailureStaysPersistingAndRetryReusesSameDraftMapping() = runBlocking {
        val dataStore = FailNextUpdateDataStore(persistingPreferences(draft))
        val creator = IdempotentDraftCreator(successResult())
        val store = FirstPromiseDraftStore(dataStore)
        val coordinator = FirstPromiseCreationCoordinator(
            creator,
            FakeDispatcher(sent = true),
            store,
            BlockingStateStore(dataStore),
            CoordinatorRecordingAnalytics(),
        )

        val failed = coordinator.persistCurrentDraft()

        assertTrue(failed is FirstPromisePersistenceResult.Failed)
        assertEquals(FirstPromisePhase.Persisting, store.readState().phase)
        assertEquals(
            FirstPromiseStateMutation.Rejected,
            store.createManualDraft(
                draft.copy(draftId = "fork"),
                requireNotNull(store.readState().recommendationReasonRef),
            ),
        )
        assertEquals(
            com.uiery.keep.domain.firstpromise.FirstPromiseEmergencyAction.WaitForPersistence,
            store.applyEmergency().action,
        )

        val recovered = coordinator.persistCurrentDraft()

        assertTrue(recovered is FirstPromisePersistenceResult.Succeeded)
        assertEquals(1, creator.createdRoutineCount)
        assertEquals(2, creator.createCalls)
        assertEquals(41L, store.readState().routineId)
        assertEquals(FirstPromisePhase.ResultEnabled, store.readState().phase)
    }

    @Test
    fun rejectedPostCommitMappingIsFailureWithoutMovingToPersistFailed() = runBlocking {
        val dataStore = persistingDataStore(draft)
        val store = FirstPromiseDraftStore(dataStore)
        val coordinator = FirstPromiseCreationCoordinator(
            FakeCreator(successResult().copy(routineId = 0L)),
            FakeDispatcher(sent = true),
            store,
            BlockingStateStore(dataStore),
            CoordinatorRecordingAnalytics(),
        )

        val result = coordinator.persistCurrentDraft()

        assertTrue(result is FirstPromisePersistenceResult.Failed)
        assertEquals(FirstPromisePhase.Persisting, store.readState().phase)
    }

    @Test
    fun cancellationDuringCreationPropagatesWithoutMarkingPersistenceFailed() {
        val dataStore = persistingDataStore(draft)
        val store = FirstPromiseDraftStore(dataStore)
        val coordinator = FirstPromiseCreationCoordinator(
            FakeCreator(failure = CancellationException("cancel creation")),
            FakeDispatcher(sent = false),
            store,
            BlockingStateStore(dataStore),
            CoordinatorRecordingAnalytics(),
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { coordinator.persistCurrentDraft() }
        }

        assertEquals(FirstPromisePhase.Persisting, runBlocking { store.readState() }.phase)
    }

    @Test
    fun pendingCreationAnalyticsNeverClaimsFirstLockConfigured() = runBlocking {
        val dataStore = persistingDataStore(draft)
        val analytics = CoordinatorRecordingAnalytics()
        val coordinator = FirstPromiseCreationCoordinator(
            FakeCreator(successResult()),
            FakeDispatcher(sent = false),
            FirstPromiseDraftStore(dataStore),
            BlockingStateStore(dataStore),
            analytics,
        )

        coordinator.persistCurrentDraft()

        assertEquals(0, analytics.firstLockCalls)
    }

    @Test
    fun firstLockAnalyticsFailureIsNonFatalAndRetriesOnNextReconciliation() = runBlocking {
        val dataStore = persistingDataStore(draft)
        val analytics = CoordinatorRecordingAnalytics(failFirstLock = true)
        val coordinator = FirstPromiseCreationCoordinator(
            FakeCreator(successResult()),
            FakeDispatcher(sent = true),
            FirstPromiseDraftStore(dataStore),
            BlockingStateStore(dataStore),
            analytics,
        )

        val first = coordinator.persistCurrentDraft()
        assertTrue(first is FirstPromisePersistenceResult.Succeeded)
        assertFalse(dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] == true)

        val retried = coordinator.persistCurrentDraft()
        assertTrue(retried is FirstPromisePersistenceResult.Succeeded)
        assertEquals(2, analytics.firstLockAttempts)
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
    }

    @Test
    fun queryAndFinalizeBoundaryKeepsMappedRoutineId() = runBlocking {
        val dataStore = persistingDataStore(draft)
        val disabled = successResult().copy(
            routine = successResult().routine.copy(isEnabled = false),
            scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
            schedulingSucceeded = false,
        )
        val creator = FakeCreator(result = disabled, finalizedResult = successResult())
        val coordinator = FirstPromiseCreationCoordinator(
            creator,
            FakeDispatcher(sent = true),
            FirstPromiseDraftStore(dataStore),
            BlockingStateStore(dataStore),
            CoordinatorRecordingAnalytics(),
        )
        coordinator.persistCurrentDraft()

        val queried = coordinator.readCurrentMapping()
        val finalized = coordinator.finalizeExistingRoutine(41L)

        assertEquals(41L, queried?.routineId)
        assertEquals(41L, (finalized as FirstPromisePersistenceResult.Succeeded).creation.routineId)
        assertEquals(listOf(41L), creator.finalizedRoutineIds)
        val state = FirstPromiseDraftStore(dataStore).readState()
        assertEquals(41L, state.routineId)
        assertEquals(FirstPromiseScheduleState.Enabled, state.scheduleState)
    }

    @Test
    fun readOnlyReconciliationDoesNotFinalizeOrTrackWhenCurrentRoutineWasDisabled() = runBlocking {
        val disabled = successResult().copy(
            routine = successResult().routine.copy(isEnabled = false),
            scheduleState = FirstPromiseScheduleState.DisabledUserChoice,
            schedulingSucceeded = false,
            draftId = draft.draftId,
        )
        val creator = FakeCreator(result = disabled, finalizedResult = successResult())
        val dispatcher = FakeDispatcher(sent = true)
        val analytics = CoordinatorRecordingAnalytics()
        val dataStore = completedEnabledDataStore()
        val store = FirstPromiseDraftStore(dataStore)
        val coordinator = FirstPromiseCreationCoordinator(
            creator,
            dispatcher,
            store,
            BlockingStateStore(dataStore),
            analytics,
        )

        val result = coordinator.reconcileExistingRoutine(41L)

        assertTrue(result is FirstPromisePersistenceResult.Succeeded)
        assertFalse((result as FirstPromisePersistenceResult.Succeeded).creation.routine.isEnabled)
        assertTrue(creator.finalizedRoutineIds.isEmpty())
        assertTrue(dispatcher.drainedDrafts.isEmpty())
        assertEquals(0, analytics.firstLockAttempts)
    }

    @Test
    fun completedDisabledFinalizeUsesDurableMappingDraftIdAfterTemporaryDraftIsCleared() = runBlocking {
        val dataStore = resultDisabledDataStore(draft)
        val store = FirstPromiseDraftStore(dataStore)
        store.completeOnboarding()
        assertNull(store.readState().draft)
        val finalized = successResult().copy(draftId = draft.draftId)
        val dispatcher = FakeDispatcher(sent = true)
        val analytics = CoordinatorRecordingAnalytics()
        val coordinator = FirstPromiseCreationCoordinator(
            FakeCreator(result = finalized.copy(scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing), finalizedResult = finalized),
            dispatcher,
            store,
            BlockingStateStore(dataStore),
            analytics,
        )

        val result = coordinator.finalizeExistingRoutine(41L)

        assertTrue(result is FirstPromisePersistenceResult.Succeeded)
        assertEquals(listOf(draft.draftId), dispatcher.drainedDrafts)
        assertEquals(1, analytics.firstLockCalls)
        assertEquals(FirstPromisePhase.CompletedEnabled, store.readState().phase)
        assertEquals(FirstPromiseScheduleState.Enabled, store.readState().scheduleState)
        assertNull(store.readState().draft)
    }

    @Test
    fun completedActivationAnalyticsFailureRetriesFromDurableMappingOnStartup() = runBlocking {
        val dataStore = resultDisabledDataStore(draft)
        val store = FirstPromiseDraftStore(dataStore)
        store.completeOnboarding()
        val finalized = successResult().copy(draftId = draft.draftId)
        val dispatcher = FakeDispatcher(sent = true)
        val analytics = CoordinatorRecordingAnalytics(failFirstLock = true)
        val coordinator = FirstPromiseCreationCoordinator(
            FakeCreator(
                result = finalized.copy(scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing),
                finalizedResult = finalized,
            ),
            dispatcher,
            store,
            BlockingStateStore(dataStore),
            analytics,
        )

        coordinator.finalizeExistingRoutine(41L)
        assertEquals(1, analytics.firstLockAttempts)
        assertEquals(0, analytics.firstLockCalls)
        assertEquals(FirstPromisePhase.CompletedEnabled, store.readState().phase)

        FirstPromiseStartupRunner(dispatcher, store, coordinator).run()

        assertEquals(2, analytics.firstLockAttempts)
        assertEquals(1, analytics.firstLockCalls)
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
    }

    @Test
    fun sentRowsCanBeCleanedBeforeSameIdEnableBecauseDurableBarrierStillTracksFirstLock() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-07-16T09:00:00Z"), ZoneOffset.UTC)
        val codec = FirstPromiseOutboxEventCodec()
        val outboxStore = FakeOutboxStore(
            listOf(
                codec.encode(
                    draft.draftId,
                    FirstPromiseOutboxEvent.RoutineSaved(
                        FirstPromiseRepeatDaysBucket.TwoThree,
                        FirstPromiseTimeWindowBucket.Overnight,
                        FirstPromiseScheduleState.DisabledExactAlarmMissing,
                    ),
                    1L,
                ),
                codec.encode(
                    draft.draftId,
                    FirstPromiseOutboxEvent.FirstPromiseCreated(
                        draft.goal,
                        draft.source,
                        FirstPromiseScheduleState.DisabledExactAlarmMissing,
                    ),
                    2L,
                ),
            ),
        )
        val barrier = FakeCreationBarrier()
        val analytics = CoordinatorRecordingAnalytics()
        val dispatcher = FirstPromiseAnalyticsDispatcher(
            store = outboxStore,
            codec = codec,
            analytics = analytics,
            clock = clock,
            creationBarrier = barrier,
        )
        dispatcher.drainDraft(draft.draftId)
        assertTrue(dispatcher.creationEventsSent(draft.draftId))
        outboxStore.ageSentRows(clock.millis() - 31L * 24 * 60 * 60 * 1000)
        dispatcher.cleanupSentRows()
        assertTrue(outboxStore.rows.isEmpty())

        val dataStore = resultDisabledDataStore(draft)
        val stateStore = FirstPromiseDraftStore(dataStore)
        stateStore.completeOnboarding()
        val finalized = successResult().copy(draftId = draft.draftId)
        val coordinator = FirstPromiseCreationCoordinator(
            FakeCreator(finalized, finalizedResult = finalized),
            dispatcher,
            stateStore,
            BlockingStateStore(dataStore),
            analytics,
        )

        val result = coordinator.finalizeExistingRoutine(41L)

        assertEquals(41L, (result as FirstPromisePersistenceResult.Succeeded).creation.routineId)
        assertEquals(1, analytics.firstLockCalls)
    }

    @Test
    fun concurrentReconciliationCannotClaimFirstLockWhileFailureOwnerHoldsMarker() = runBlocking {
        val dataStore = persistingDataStore(draft)
        val analytics = BlockingFailingFirstLockAnalytics()
        val coordinator = FirstPromiseCreationCoordinator(
            FakeCreator(successResult()),
            FakeDispatcher(sent = true),
            FirstPromiseDraftStore(dataStore),
            BlockingStateStore(dataStore),
            analytics,
        )

        val owner = async(Dispatchers.Default) { coordinator.persistCurrentDraft() }
        assertTrue(analytics.entered.await(5, TimeUnit.SECONDS))
        val concurrent = async(Dispatchers.Default) { coordinator.persistCurrentDraft() }
        assertTrue(concurrent.await() is FirstPromisePersistenceResult.Succeeded)
        assertEquals(1, analytics.attempts)

        analytics.release.countDown()
        assertTrue(owner.await() is FirstPromisePersistenceResult.Succeeded)
        assertFalse(dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] == true)
    }

    private fun persistingDataStore(draft: FirstPromiseDraft) = FakeDataStore(persistingPreferences(draft))

    private fun persistingPreferences(draft: FirstPromiseDraft) = mutablePreferencesOf(
            PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(
                FirstPromiseOnboardingState(
                    phase = FirstPromisePhase.Persisting,
                    path = FirstPromisePath.Manual,
                    goal = draft.goal,
                    draft = draft,
                    recommendationReasonRef = RecommendationReasonRef(
                        patternType = UsagePatternType.Manual,
                        usageCoverageDays = 0,
                        eventCoverageDays = 0,
                        isGoalDefault = true,
                        selectedStartMinutes = draft.startMinutes,
                    ),
                ),
            ),
    )

    private fun resultDisabledDataStore(draft: FirstPromiseDraft) = FakeDataStore(
        mutablePreferencesOf(
            PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(
                FirstPromiseOnboardingState(
                    phase = FirstPromisePhase.ResultDisabled,
                    path = FirstPromisePath.Manual,
                    goal = draft.goal,
                    draft = draft,
                    routineId = 41L,
                    scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
                    recommendationReasonRef = RecommendationReasonRef(
                        patternType = UsagePatternType.Manual,
                        usageCoverageDays = 0,
                        eventCoverageDays = 0,
                        isGoalDefault = true,
                        selectedStartMinutes = draft.startMinutes,
                    ),
                ),
            ),
        ),
    )

    private fun completedEnabledDataStore() = FakeDataStore(
        mutablePreferencesOf(
            PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(
                FirstPromiseOnboardingState(
                    phase = FirstPromisePhase.CompletedEnabled,
                    path = FirstPromisePath.Manual,
                    goal = draft.goal,
                    routineId = 41L,
                    scheduleState = FirstPromiseScheduleState.Enabled,
                ),
            ),
        ),
    )

    private fun successResult() = FirstPromiseCreationResult(
        routineId = 41L,
        routine = RoutineModel(
            id = 41L,
            name = "Study",
            startTime = kotlinx.datetime.LocalTime(23, 45),
            endTime = kotlinx.datetime.LocalTime(0, 15),
            repeatDays = "1110101",
            lockApplications = listOf(draft.packageName),
            isEnabled = true,
        ),
        scheduleState = FirstPromiseScheduleState.Enabled,
        schedulingSucceeded = true,
        created = true,
    )
}

private class FailNextUpdateDataStore(initial: Preferences) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private var shouldFail = true
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        if (shouldFail) {
            shouldFail = false
            error("datastore unavailable after Room commit")
        }
        return transform(state.value).also { state.value = it }
    }
}

private class IdempotentDraftCreator(
    private val committed: FirstPromiseCreationResult,
) : FirstPromiseCreator {
    var createCalls = 0
    var createdRoutineCount = 0
    private var existing: FirstPromiseCreationResult? = null

    override suspend fun createFirstPromise(
        draft: FirstPromiseDraft,
        routine: RoutineModel,
    ): FirstPromiseCreationResult {
        createCalls++
        existing?.let { return it.copy(created = false) }
        createdRoutineCount++
        return committed.copy(draftId = draft.draftId).also { existing = it }
    }

    override suspend fun findExistingByDraftId(draftId: String): FirstPromiseCreationResult? = existing
    override suspend fun findExistingByRoutineId(routineId: Long): FirstPromiseCreationResult? =
        existing?.takeIf { it.routineId == routineId }
    override suspend fun finalizeExistingRoutine(routineId: Long): FirstPromiseCreationResult? = null
}

private class FakeCreator(
    result: FirstPromiseCreationResult? = null,
    private val failure: Throwable? = null,
    private val finalizedResult: FirstPromiseCreationResult? = null,
) : FirstPromiseCreator {
    private var currentResult = result
    var lastRoutine: RoutineModel? = null
    val finalizedRoutineIds = mutableListOf<Long>()
    override suspend fun createFirstPromise(draft: FirstPromiseDraft, routine: RoutineModel): FirstPromiseCreationResult {
        failure?.let { throw it }
        lastRoutine = routine
        return requireNotNull(currentResult)
    }

    override suspend fun findExistingByDraftId(draftId: String): FirstPromiseCreationResult? = currentResult

    override suspend fun findExistingByRoutineId(routineId: Long): FirstPromiseCreationResult? =
        currentResult?.takeIf { it.routineId == routineId }

    override suspend fun finalizeExistingRoutine(routineId: Long): FirstPromiseCreationResult? {
        finalizedRoutineIds += routineId
        return finalizedResult?.also { currentResult = it }
    }
}

private class FakeDispatcher(private val sent: Boolean) : FirstPromiseOutboxDispatcher {
    val drainedDrafts = mutableListOf<String>()
    override suspend fun drainAll() = Unit
    override suspend fun drainDraft(draftId: String) {
        drainedDrafts += draftId
    }
    override suspend fun cleanupSentRows() = Unit
    override suspend fun creationEventsSent(draftId: String): Boolean = sent
}

private class CoordinatorRecordingAnalytics(
    private var failFirstLock: Boolean = false,
) : KeepAnalytics {
    var firstLockCalls = 0
    var firstLockAttempts = 0
    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) {
        firstLockAttempts++
        if (failFirstLock) {
            failFirstLock = false
            error("analytics unavailable")
        }
        firstLockCalls++
    }
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}

private class BlockingFailingFirstLockAnalytics : KeepAnalytics {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    var attempts = 0

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) {
        attempts++
        entered.countDown()
        check(release.await(5, TimeUnit.SECONDS))
        error("analytics unavailable")
    }
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}

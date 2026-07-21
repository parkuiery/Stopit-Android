package com.uiery.keep.data.firstpromise

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.analytics.FirstLockConfiguredDeliveryCoordinator
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.PendingOnboardingAnalyticsEvent
import com.uiery.keep.feature.onboarding.FirstPromiseAnalyticsCall
import com.uiery.keep.feature.onboarding.FirstPromiseRecordingAnalytics
import com.uiery.keep.domain.firstpromise.RecommendationReasonRef
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.model.RoutineModel
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPromiseStartupRunnerTest {
    @Test
    fun processDeathAfterFirstLockReservationRetriesPendingAnalyticsOnStartup() = runBlocking {
        val dataStore = FakeDataStore(mutablePreferencesOf())
        val firstLockCalls = mutableListOf<Pair<String, Int?>>()
        val analytics = object : KeepAnalytics by FirstPromiseRecordingAnalytics() {
            override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) {
                firstLockCalls += source to selectedAppCount
            }
        }
        val delivery = FirstLockConfiguredDeliveryCoordinator(
            blockingStateStore = com.uiery.keep.datastore.BlockingStateStore(dataStore),
            analytics = analytics,
        )

        assertTrue(
            delivery.reserveIfNeeded(
                source = AnalyticsSource.ONBOARDING,
                selectedAppCount = 1,
            ),
        )
        assertFalse(dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] == true)
        assertEquals(
            AnalyticsSource.ONBOARDING,
            dataStore.snapshot()[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE],
        )

        val dispatcher = StartupDispatcher(mutableListOf())
        FirstPromiseStartupRunner(dispatcher, delivery).run()
        FirstPromiseStartupRunner(dispatcher, delivery).run()

        assertEquals(listOf(AnalyticsSource.ONBOARDING to 1), firstLockCalls)
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
        assertEquals(null, dataStore.snapshot()[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE])
    }

    @Test
    fun committedCompletionEventIsDrainedOnNextProcessStartup() = runBlocking {
        val initial = FirstPromiseOnboardingState(
            phase = FirstPromisePhase.ResultDisabled,
            routineId = 77L,
            scheduleState = FirstPromiseScheduleState.DisabledUserChoice,
        )
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(initial),
                PreferencesKey.IS_NEW to true,
            ),
        )
        val store = FirstPromiseDraftStore(dataStore)
        assertTrue(
            store.completeOnboarding() is
                com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation.Changed,
        )
        assertEquals(false, dataStore.snapshot()[PreferencesKey.IS_NEW])
        assertEquals(
            listOf(PendingOnboardingAnalyticsEvent.PromiseResultStepComplete),
            store.readState().pendingOnboardingAnalyticsEvents,
        )
        val analytics = FirstPromiseRecordingAnalytics()
        val calls = mutableListOf<String>()

        FirstPromiseStartupRunner(
            dispatcher = StartupDispatcher(calls),
            draftStore = store,
            creationCoordinator = StartupPersistenceCoordinator(store, calls),
            onboardingAnalyticsDispatcher = FirstPromiseOnboardingAnalyticsDispatcher(store, analytics),
        ).run()

        assertEquals(
            1,
            analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.PROMISE_RESULT) },
        )
        assertTrue(store.readState().pendingOnboardingAnalyticsEvents.isEmpty())
    }

    @Test
    fun cancellationFromOnboardingDrainPropagatesAndStopsGlobalOutboxWork() {
        val state = FirstPromiseOnboardingState(
            phase = FirstPromisePhase.CompletedDisabled,
            routineId = 77L,
            scheduleState = FirstPromiseScheduleState.DisabledUserChoice,
            pendingOnboardingAnalyticsEvents = listOf(
                PendingOnboardingAnalyticsEvent.PromiseResultStepComplete,
            ),
        )
        val store = FirstPromiseDraftStore(
            FakeDataStore(
                mutablePreferencesOf(
                    PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
                    PreferencesKey.IS_NEW to false,
                ),
            ),
        )
        val calls = mutableListOf<String>()
        val analytics = object : KeepAnalytics by FirstPromiseRecordingAnalytics() {
            override fun trackOnboardingStepComplete(stepName: String) {
                throw CancellationException("cancel onboarding drain")
            }
        }
        val runner = FirstPromiseStartupRunner(
            dispatcher = StartupDispatcher(calls),
            draftStore = store,
            creationCoordinator = StartupPersistenceCoordinator(store, calls),
            onboardingAnalyticsDispatcher = FirstPromiseOnboardingAnalyticsDispatcher(store, analytics),
        )

        assertThrows(CancellationException::class.java) { runBlocking { runner.run() } }

        assertEquals(emptyList<String>(), calls)
        assertEquals(
            listOf(PendingOnboardingAnalyticsEvent.PromiseResultStepComplete),
            runBlocking { store.readState().pendingOnboardingAnalyticsEvents },
        )
    }

    @Test
    fun persistingStateReconcilesThroughCoordinatorBeforeGlobalDrainAndCleanup() = runBlocking {
        val draft = FirstPromiseDraft(
            draftId = "draft",
            goal = FirstPromiseGoal.Focus,
            packageName = "com.example.focus",
            appLabel = "Focus",
            startMinutes = 22 * 60,
            repeatDays = setOf(1),
            source = FirstPromiseSource.Manual,
        )
        val store = FirstPromiseDraftStore(
            FakeDataStore(
                mutablePreferencesOf(
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
                ),
            ),
        )
        val calls = mutableListOf<String>()
        val coordinator = StartupPersistenceCoordinator(store, calls)
        val dispatcher = StartupDispatcher(calls)

        FirstPromiseStartupRunner(dispatcher, store, coordinator).run()

        assertEquals(listOf("persist", "drain", "cleanup"), calls)
        assertEquals(FirstPromisePhase.ResultEnabled, store.readState().phase)
        assertEquals(77L, store.readState().routineId)
    }

    @Test
    fun staleEnabledStateUsesReadOnlyReconciliationAndNeverFinalizesDisabledRoutine() = runBlocking {
        val state = FirstPromiseOnboardingState(
            phase = FirstPromisePhase.CompletedEnabled,
            path = FirstPromisePath.Manual,
            goal = FirstPromiseGoal.Focus,
            routineId = 77L,
            scheduleState = FirstPromiseScheduleState.Enabled,
        )
        val store = FirstPromiseDraftStore(
            FakeDataStore(
                mutablePreferencesOf(
                    PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
                ),
            ),
        )
        val calls = mutableListOf<String>()
        val coordinator = StartupPersistenceCoordinator(store, calls, currentRoutineEnabled = false)

        FirstPromiseStartupRunner(StartupDispatcher(calls), store, coordinator).run()

        assertEquals(listOf("reconcile:77:false", "drain", "cleanup"), calls)
        assertEquals(0, coordinator.finalizeCalls)
        assertFalse(coordinator.currentRoutineEnabled)
    }

    @Test
    fun cancellationFromReconciliationPropagatesAndStopsLaterRecovery() {
        val state = FirstPromiseOnboardingState(
            phase = FirstPromisePhase.CompletedEnabled,
            routineId = 77L,
            scheduleState = FirstPromiseScheduleState.Enabled,
        )
        val store = FirstPromiseDraftStore(
            FakeDataStore(
                mutablePreferencesOf(
                    PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
                ),
            ),
        )
        val calls = mutableListOf<String>()
        val coordinator = StartupPersistenceCoordinator(
            store,
            calls,
            reconciliationFailure = CancellationException("cancel startup"),
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { FirstPromiseStartupRunner(StartupDispatcher(calls), store, coordinator).run() }
        }

        assertEquals(listOf("reconcile:77:true"), calls)
    }
}

private class StartupPersistenceCoordinator(
    private val store: FirstPromiseDraftStore,
    private val calls: MutableList<String>,
    var currentRoutineEnabled: Boolean = true,
    private val reconciliationFailure: Throwable? = null,
) : FirstPromisePersistenceCoordinator {
    var finalizeCalls = 0
    override suspend fun persistCurrentDraft(): FirstPromisePersistenceResult {
        calls += "persist"
        val creation = FirstPromiseCreationResult(
            routineId = 77L,
            routine = RoutineModel(
                id = 77L,
                name = "Focus",
                startTime = kotlinx.datetime.LocalTime(22, 0),
                endTime = kotlinx.datetime.LocalTime(22, 30),
                repeatDays = "1000000",
                lockApplications = listOf("com.example.focus"),
                isEnabled = true,
            ),
            scheduleState = FirstPromiseScheduleState.Enabled,
            schedulingSucceeded = true,
            created = true,
        )
        store.recordPersistenceMapping(creation.routineId, creation.scheduleState)
        return FirstPromisePersistenceResult.Succeeded(creation)
    }

    override suspend fun readCurrentMapping(): FirstPromiseCreationResult? = null

    override suspend fun reconcileExistingRoutine(routineId: Long): FirstPromisePersistenceResult {
        calls += "reconcile:$routineId:$currentRoutineEnabled"
        reconciliationFailure?.let { throw it }
        return FirstPromisePersistenceResult.MissingRoutine
    }

    override suspend fun finalizeExistingRoutine(routineId: Long): FirstPromisePersistenceResult {
        finalizeCalls++
        currentRoutineEnabled = true
        return FirstPromisePersistenceResult.MissingRoutine
    }
}

private class StartupDispatcher(
    private val calls: MutableList<String>,
) : FirstPromiseOutboxDispatcher {
    override suspend fun drainAll() {
        calls += "drain"
    }

    override suspend fun drainDraft(draftId: String) = Unit

    override suspend fun cleanupSentRows() {
        calls += "cleanup"
    }

    override suspend fun creationEventsSent(draftId: String): Boolean = false
}

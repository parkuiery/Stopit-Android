package com.uiery.keep.data.firstpromise

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
import com.uiery.keep.domain.firstpromise.RecommendationReasonRef
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.model.RoutineModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    private fun persistingDataStore(draft: FirstPromiseDraft) = FakeDataStore(
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

private class FakeCreator(
    private val result: FirstPromiseCreationResult? = null,
    private val failure: Throwable? = null,
) : FirstPromiseCreator {
    var lastRoutine: RoutineModel? = null
    override suspend fun createFirstPromise(draft: FirstPromiseDraft, routine: RoutineModel): FirstPromiseCreationResult {
        failure?.let { throw it }
        lastRoutine = routine
        return requireNotNull(result)
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

private class CoordinatorRecordingAnalytics : KeepAnalytics {
    var firstLockCalls = 0
    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) { firstLockCalls++ }
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}

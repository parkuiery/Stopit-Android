package com.uiery.keep.data.firstpromise

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
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
import org.junit.Test

class FirstPromiseStartupRunnerTest {
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
}

private class StartupPersistenceCoordinator(
    private val store: FirstPromiseDraftStore,
    private val calls: MutableList<String>,
) : FirstPromisePersistenceCoordinator {
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

    override suspend fun finalizeExistingRoutine(routineId: Long): FirstPromisePersistenceResult =
        FirstPromisePersistenceResult.MissingRoutine
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

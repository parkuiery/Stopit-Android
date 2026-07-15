package com.uiery.keep.analytics

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseRecommendationPolicy
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.PendingOnboardingAnalyticsEvent
import com.uiery.keep.domain.firstpromise.UsagePermissionAttempt
import com.uiery.keep.domain.firstpromise.UsagePermissionLaunchState
import com.uiery.keep.domain.firstpromise.UsagePermissionOutcome
import com.uiery.keep.feature.onboarding.FirstPromiseAnalyticsCall
import com.uiery.keep.feature.onboarding.FirstPromiseRecordingAnalytics
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPromiseOnboardingAnalyticsDispatcherTest {

    @Test
    fun milestoneMutationsAtomicallyQueueTypedEventsWithTheManualDraft() = runBlocking {
        val store = store(initialState(OnboardingVariant.PromiseCoachV1))

        assertTrue(store.markExposedIfNeeded(OnboardingVariant.PromiseCoachV1))
        assertTrue(store.markGoalSelectViewed() is FirstPromiseStateMutation.Changed)
        assertTrue(store.chooseManualGoal() is FirstPromiseStateMutation.Changed)
        val proposal = FirstPromiseRecommendationPolicy.fromSelection(
            draftId = "draft-local",
            goal = FirstPromiseGoal.Unspecified,
            packageName = "com.example.video",
            appLabel = "Video",
        )
        assertTrue(
            store.createManualDraft(
                proposal.draft,
                FirstPromiseRecommendationPolicy.toReasonRef(proposal),
            ) is FirstPromiseStateMutation.Changed,
        )

        val state = store.readState()
        assertEquals(FirstPromisePhase.DraftReady, state.phase)
        assertEquals("com.example.video", state.draft?.packageName)
        assertTrue(FirstPromiseMilestone.AppSelection in state.trackedMilestones)
        assertEquals(
            listOf(
                PendingOnboardingAnalyticsEvent.ExperimentExposurePromiseCoachV1,
                PendingOnboardingAnalyticsEvent.GoalSelectStepView,
                PendingOnboardingAnalyticsEvent.GoalSelectStepComplete,
                PendingOnboardingAnalyticsEvent.SelectAppStepComplete,
                PendingOnboardingAnalyticsEvent.AppSelectionCompletedSingle,
            ),
            state.pendingOnboardingAnalyticsEvents,
        )
    }

    @Test
    fun recreationDrainsTypedEventsInOrderAndClearsOnlyDeliveredItems() = runBlocking {
        val analytics = FirstPromiseRecordingAnalytics()
        val store = store(
            initialState(OnboardingVariant.PromiseCoachV1).copy(
                pendingOnboardingAnalyticsEvents = listOf(
                    PendingOnboardingAnalyticsEvent.ExperimentExposurePromiseCoachV1,
                    PendingOnboardingAnalyticsEvent.GoalSelectStepView,
                    PendingOnboardingAnalyticsEvent.GoalSelectStepComplete,
                    PendingOnboardingAnalyticsEvent.SelectAppStepComplete,
                    PendingOnboardingAnalyticsEvent.AppSelectionCompletedSingle,
                ),
            ),
        )

        FirstPromiseOnboardingAnalyticsDispatcher(store, analytics).drain()

        assertEquals(
            listOf(
                FirstPromiseAnalyticsCall.Exposure(OnboardingVariant.PromiseCoachV1),
                FirstPromiseAnalyticsCall.StepView(OnboardingStepName.GOAL_SELECT),
                FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.GOAL_SELECT),
                FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.SELECT_APP),
                FirstPromiseAnalyticsCall.AppSelectionCompletedSingle,
            ),
            analytics.calls,
        )
        assertTrue(store.readState().pendingOnboardingAnalyticsEvents.isEmpty())

        FirstPromiseOnboardingAnalyticsDispatcher(store, analytics).drain()
        assertEquals(5, analytics.calls.size)
    }

    @Test
    fun failedDeliveryLeavesTheHeadAndFollowingEventsForRestart() = runBlocking {
        val store = store(
            initialState(OnboardingVariant.Control).copy(
                pendingOnboardingAnalyticsEvents = listOf(
                    PendingOnboardingAnalyticsEvent.ExperimentExposureControlV1,
                    PendingOnboardingAnalyticsEvent.GoalSelectStepView,
                ),
            ),
        )
        val failing = object : KeepAnalytics by FirstPromiseRecordingAnalytics() {
            override fun trackOnboardingExperimentExposed(
                variant: OnboardingVariant,
                assignmentVersion: OnboardingAssignmentVersion,
            ) = error("delivery interrupted")
        }

        val result = runCatching {
            FirstPromiseOnboardingAnalyticsDispatcher(store, failing).drain()
        }

        assertTrue(result.isFailure)
        assertEquals(
            listOf(
                PendingOnboardingAnalyticsEvent.ExperimentExposureControlV1,
                PendingOnboardingAnalyticsEvent.GoalSelectStepView,
            ),
            store.readState().pendingOnboardingAnalyticsEvents,
        )
    }

    @Test
    fun usageVisibilityAndCompletionAreDurableButContainNoSensitivePayload() = runBlocking {
        val store = store(initialState(OnboardingVariant.PromiseCoachV1).copy(
            phase = FirstPromisePhase.UsageAccessPending,
            goal = FirstPromiseGoal.Focus,
            usagePermissionAttempt = UsagePermissionAttempt(
                id = 1L,
                launchState = UsagePermissionLaunchState.Opened,
                terminalOutcome = UsagePermissionOutcome.Granted,
            ),
        ))

        assertTrue(store.markUsageAccessViewed() is FirstPromiseStateMutation.Changed)
        assertTrue(store.completeUsageAccess() is FirstPromiseStateMutation.Changed)
        val state = store.readState()
        assertEquals(
            listOf(
                PendingOnboardingAnalyticsEvent.UsageAccessStepView,
                PendingOnboardingAnalyticsEvent.UsageAccessStepComplete,
            ),
            state.pendingOnboardingAnalyticsEvents,
        )
        val json = Json.encodeToString(state)

        assertFalse(json.contains("com.example"))
        assertFalse(json.contains("draftId"))
        assertFalse(json.contains("packageName"))
        assertFalse(json.contains("averageDailyMinutes"))
        assertFalse(json.contains("routineId"))
        assertFalse(json.contains("attemptId"))
    }

    private fun initialState(variant: OnboardingVariant) = FirstPromiseOnboardingState(
        assignment = variant,
        assignmentVersion = OnboardingAssignmentVersion.V1,
        phase = FirstPromisePhase.GoalPending,
        path = FirstPromisePath.Personalized,
    )

    private fun store(state: FirstPromiseOnboardingState): FirstPromiseDraftStore =
        FirstPromiseDraftStore(
            FakeDataStore(
                mutablePreferencesOf(
                    PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
                ),
            ),
        )
}

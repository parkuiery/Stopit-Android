package com.uiery.keep.feature.onboarding.select

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectAppViewModelTest {

    @Test
    fun controlConfirmationKeepsEveryPackageAndCompletesExistingOnboarding() = runBlocking {
        val dataStore = FakeDataStore()
        val analytics = RecordingAnalytics()
        val viewModel = SelectAppViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            analytics = analytics,
        )

        viewModel.selectCategoryComplete(setOf("com.example.one", "com.example.two"))
        delay(100)

        assertEquals(
            setOf("com.example.one", "com.example.two"),
            dataStore.snapshot()[PreferencesKey.SELECTED_APP_PACKAGES],
        )
        assertEquals(false, dataStore.snapshot()[PreferencesKey.IS_NEW])
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
        assertEquals(
            listOf(
                AnalyticsCall.AppSelectionCompleted(selectedAppCount = 2, isOnboarding = true),
                AnalyticsCall.StepCompleted(OnboardingStepName.SELECT_APP),
                AnalyticsCall.FirstLockConfigured(selectedAppCount = 2),
            ),
            analytics.calls,
        )
    }

    @Test
    fun manualConfirmationStoresOnePromiseAndMilestoneBeforeRoutingProposal() = runBlocking {
        val dataStore = manualSelectionDataStore(goal = FirstPromiseGoal.Sleep)
        val analytics = RecordingAnalytics()
        val viewModel = SelectAppViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            analytics = analytics,
            draftStore = FirstPromiseDraftStore(dataStore),
            draftId = { "draft-1" },
        )
        val navigation = async { viewModel.container.sideEffectFlow.first() }

        viewModel.selectManualCategoryComplete(
            packageName = "com.example.video",
            appLabel = "Video",
        )

        assertEquals(SelectAppSideEffect.NavigateProposal, navigation.await())
        val state = FirstPromiseDraftStore(dataStore).readState()
        assertEquals(FirstPromisePhase.DraftReady, state.phase)
        assertEquals("draft-1", state.draft?.draftId)
        assertEquals("com.example.video", state.draft?.packageName)
        assertEquals("Video", state.draft?.appLabel)
        assertEquals(FirstPromiseGoal.Sleep, state.draft?.goal)
        assertEquals(FirstPromiseSource.GoalTemplate, state.draft?.source)
        assertTrue(FirstPromiseMilestone.AppSelection in state.trackedMilestones)
        assertEquals(state.draft?.startMinutes, state.recommendationReasonRef?.selectedStartMinutes)
        assertEquals(
            listOf(
                AnalyticsCall.AppSelectionCompleted(selectedAppCount = 1, isOnboarding = true),
                AnalyticsCall.StepCompleted(OnboardingStepName.SELECT_APP),
            ),
            analytics.calls,
        )
        assertNull(dataStore.snapshot()[PreferencesKey.SELECTED_APP_PACKAGES])
        assertNull(dataStore.snapshot()[PreferencesKey.IS_NEW])
        assertNull(dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
    }

    @Test
    fun manualRetryDoesNotReplaceDraftOrRepeatCompletion() = runBlocking {
        val dataStore = manualSelectionDataStore(goal = FirstPromiseGoal.Unspecified)
        val analytics = RecordingAnalytics()
        val viewModel = SelectAppViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            analytics = analytics,
            draftStore = FirstPromiseDraftStore(dataStore),
            draftId = { "draft-1" },
        )
        val firstNavigation = async { viewModel.container.sideEffectFlow.first() }
        viewModel.selectManualCategoryComplete("com.example.first", "First")
        assertEquals(SelectAppSideEffect.NavigateProposal, firstNavigation.await())

        viewModel.selectManualCategoryComplete("com.example.second", "Second")
        delay(100)

        val state = FirstPromiseDraftStore(dataStore).readState()
        assertEquals("com.example.first", state.draft?.packageName)
        assertEquals(FirstPromiseSource.Manual, state.draft?.source)
        assertEquals(2, analytics.calls.size)
        assertNull(withTimeoutOrNull(100) { viewModel.container.sideEffectFlow.first() })
    }

    private fun manualSelectionDataStore(goal: FirstPromiseGoal): FakeDataStore {
        val state = FirstPromiseOnboardingState(
            assignment = OnboardingVariant.PromiseCoachV1,
            assignmentVersion = OnboardingAssignmentVersion.V1,
            phase = FirstPromisePhase.ManualSelectPending,
            path = FirstPromisePath.Manual,
            goal = goal,
        )
        return FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
            ),
        )
    }
}

private sealed interface AnalyticsCall {
    data class StepCompleted(val stepName: String) : AnalyticsCall
    data class AppSelectionCompleted(val selectedAppCount: Int, val isOnboarding: Boolean) : AnalyticsCall
    data class FirstLockConfigured(val selectedAppCount: Int?) : AnalyticsCall
}

private class RecordingAnalytics : KeepAnalytics {
    val calls = mutableListOf<AnalyticsCall>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) {
        calls += AnalyticsCall.StepCompleted(stepName)
    }
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) {
        calls += AnalyticsCall.FirstLockConfigured(selectedAppCount)
    }
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
    override fun trackAppSelectionCompleted(selectedAppCount: Int, isOnboarding: Boolean) {
        calls += AnalyticsCall.AppSelectionCompleted(selectedAppCount, isOnboarding)
    }
}

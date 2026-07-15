package com.uiery.keep.feature.onboarding.goal

import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.feature.onboarding.FirstPromiseAnalyticsCall
import com.uiery.keep.feature.onboarding.FirstPromiseRecordingAnalytics
import com.uiery.keep.feature.onboarding.firstPromiseStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalSelectViewModelTest {
    @Test
    fun primaryCtaIsDisabledUntilOneGoalIsSelected() = runBlocking {
        val viewModel = GoalSelectViewModel(FirstPromiseRecordingAnalytics(), firstPromiseStore(FirstPromisePhase.GoalPending), Dispatchers.Unconfined)
        assertFalse(viewModel.container.stateFlow.value.canContinue)
        viewModel.selectGoal(FirstPromiseGoal.Sleep)
        delay(20)
        assertTrue(viewModel.container.stateFlow.value.canContinue)
        assertEquals(FirstPromiseGoal.Sleep, viewModel.container.stateFlow.value.selectedGoal)
    }

    @Test
    fun directManualUsesUnspecifiedAndStepSignalsAreExactlyOnce() = runBlocking {
        val analytics = FirstPromiseRecordingAnalytics()
        val store = firstPromiseStore(FirstPromisePhase.GoalPending)
        val viewModel = GoalSelectViewModel(analytics, store, Dispatchers.Unconfined)

        viewModel.onStepViewed(); viewModel.onStepViewed(); delay(20)
        viewModel.chooseManual(); viewModel.chooseManual(); delay(20)

        val state = store.readState()
        assertEquals(FirstPromiseGoal.Unspecified, state.goal)
        assertEquals(FirstPromisePath.Manual, state.path)
        assertEquals(FirstPromisePhase.ManualSelectPending, state.phase)
        assertEquals(setOf(FirstPromiseMilestone.Exposure), state.trackedMilestones)
        assertEquals(1, analytics.calls.count { it == FirstPromiseAnalyticsCall.StepView(OnboardingStepName.GOAL_SELECT) })
        assertEquals(1, analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.GOAL_SELECT) })
        assertEquals(1, analytics.calls.count { it is FirstPromiseAnalyticsCall.Exposure })
    }

    @Test
    fun immediateClickAwaitsDurableExposureBeforeCompletingAndNavigating() = runBlocking {
        val analytics = FirstPromiseRecordingAnalytics()
        val store = firstPromiseStore(FirstPromisePhase.GoalPending)
        val viewModel = GoalSelectViewModel(analytics, store, Dispatchers.Unconfined)
        val navigation = async { viewModel.container.sideEffectFlow.first() }

        viewModel.onStepViewed()
        viewModel.selectGoal(FirstPromiseGoal.Focus)
        viewModel.continuePersonalized()
        viewModel.onStepViewed()
        viewModel.continuePersonalized()

        assertEquals(GoalSelectSideEffect.NavigateUsageAccess, navigation.await())
        assertEquals(FirstPromisePhase.UsageAccessPending, store.readState().phase)
        assertEquals(1, analytics.calls.count { it is FirstPromiseAnalyticsCall.Exposure })
        assertEquals(1, analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.GOAL_SELECT) })
        assertTrue(
            analytics.calls.indexOfFirst { it is FirstPromiseAnalyticsCall.Exposure } <
                analytics.calls.indexOfFirst { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.GOAL_SELECT) },
        )
    }
}

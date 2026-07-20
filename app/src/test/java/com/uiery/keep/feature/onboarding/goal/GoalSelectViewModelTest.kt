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
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalSelectViewModelTest {
    @Test
    fun primaryCtaIsDisabledUntilOneGoalIsSelected() = runBlocking {
        val store = firstPromiseStore(FirstPromisePhase.GoalPending)
        val viewModel = GoalSelectViewModel(FirstPromiseRecordingAnalytics(), store, Dispatchers.Unconfined)
        assertFalse(viewModel.container.stateFlow.value.canContinue)
        viewModel.selectGoal(FirstPromiseGoal.Sleep)
        delay(20)
        assertTrue(viewModel.container.stateFlow.value.canContinue)
        assertEquals(FirstPromiseGoal.Sleep, viewModel.container.stateFlow.value.selectedGoal)
        assertTrue(store.readState().pendingOnboardingAnalyticsEvents.isEmpty())
        assertFalse(FirstPromiseMilestone.GoalSelectView in store.readState().trackedMilestones)
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
        assertEquals(
            setOf(
                FirstPromiseMilestone.Exposure,
                FirstPromiseMilestone.GoalSelectView,
                FirstPromiseMilestone.GoalSelectCompletion,
            ),
            state.trackedMilestones,
        )
        assertEquals(1, analytics.calls.count { it == FirstPromiseAnalyticsCall.StepView(OnboardingStepName.GOAL_SELECT) })
        assertEquals(1, analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.GOAL_SELECT) })
        assertEquals(1, analytics.calls.count { it is FirstPromiseAnalyticsCall.Exposure })
    }

    @Test
    fun retainedViewModelCanReviseGoalAcrossManualAndUsageAccessBackNavigation() = runBlocking {
        val analytics = FirstPromiseRecordingAnalytics()
        val store = firstPromiseStore(FirstPromisePhase.GoalPending)
        val initialViewModel = GoalSelectViewModel(analytics, store, Dispatchers.Unconfined)
        val manualNavigation = async { initialViewModel.container.sideEffectFlow.first() }

        initialViewModel.onStepViewed()
        initialViewModel.chooseManual()

        assertEquals(
            GoalSelectSideEffect.NavigateManualAppSelect,
            withTimeout(1_000) { manualNavigation.await() },
        )
        assertEquals(FirstPromisePhase.ManualSelectPending, store.readState().phase)

        val personalizedNavigation = async { initialViewModel.container.sideEffectFlow.first() }
        initialViewModel.onStepViewed()
        initialViewModel.selectGoal(FirstPromiseGoal.Focus)
        initialViewModel.continuePersonalized()

        assertEquals(
            GoalSelectSideEffect.NavigateUsageAccess,
            withTimeout(1_000) { personalizedNavigation.await() },
        )
        val state = store.readState()
        assertEquals(FirstPromisePhase.UsageAccessPending, state.phase)
        assertEquals(FirstPromisePath.Personalized, state.path)
        assertEquals(FirstPromiseGoal.Focus, state.goal)

        val revisedPersonalizedNavigation = async { initialViewModel.container.sideEffectFlow.first() }
        initialViewModel.onStepViewed()
        initialViewModel.selectGoal(FirstPromiseGoal.Study)
        initialViewModel.continuePersonalized()

        assertEquals(
            GoalSelectSideEffect.NavigateUsageAccess,
            withTimeout(1_000) { revisedPersonalizedNavigation.await() },
        )
        val revisedState = store.readState()
        assertEquals(FirstPromisePhase.UsageAccessPending, revisedState.phase)
        assertEquals(FirstPromisePath.Personalized, revisedState.path)
        assertEquals(FirstPromiseGoal.Study, revisedState.goal)
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

    @Test
    fun personalizedAndManualCrossTapsCommitOnlyTheFirstChoice() = runBlocking {
        listOf(true, false).forEach { personalizedFirst ->
            val analytics = FirstPromiseRecordingAnalytics()
            val store = firstPromiseStore(FirstPromisePhase.GoalPending)
            val viewModel = GoalSelectViewModel(analytics, store, Dispatchers.Unconfined)
            val navigation = async { viewModel.container.sideEffectFlow.first() }
            viewModel.onStepViewed()
            viewModel.selectGoal(FirstPromiseGoal.Focus)
            delay(10)

            if (personalizedFirst) {
                viewModel.continuePersonalized()
                viewModel.chooseManual()
            } else {
                viewModel.chooseManual()
                viewModel.continuePersonalized()
            }

            assertEquals(
                if (personalizedFirst) GoalSelectSideEffect.NavigateUsageAccess else GoalSelectSideEffect.NavigateManualAppSelect,
                navigation.await(),
            )
            val state = store.readState()
            assertEquals(
                if (personalizedFirst) FirstPromisePhase.UsageAccessPending else FirstPromisePhase.ManualSelectPending,
                state.phase,
            )
            assertEquals(1, analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.GOAL_SELECT) })
        }
    }
}

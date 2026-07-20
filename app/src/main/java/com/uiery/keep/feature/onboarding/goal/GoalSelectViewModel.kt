package com.uiery.keep.feature.onboarding.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.feature.onboarding.usageanalysis.FirstPromiseAnalysisTransientHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

data class GoalSelectUiState(val selectedGoal: FirstPromiseGoal? = null) {
    val canContinue: Boolean get() = selectedGoal != null
}

sealed interface GoalSelectSideEffect {
    data object NavigateUsageAccess : GoalSelectSideEffect
    data object NavigateManualAppSelect : GoalSelectSideEffect
}

@HiltViewModel
class GoalSelectViewModel internal constructor(
    private val analytics: KeepAnalytics,
    private val draftStore: FirstPromiseDraftStore,
    private val persistenceDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher =
        FirstPromiseOnboardingAnalyticsDispatcher(draftStore, analytics),
) : ViewModel(), ContainerHost<GoalSelectUiState, GoalSelectSideEffect> {
    @Inject constructor(
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore,
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
    ) : this(analytics, draftStore, Dispatchers.IO, onboardingAnalyticsDispatcher)

    override val container: Container<GoalSelectUiState, GoalSelectSideEffect> = container(GoalSelectUiState())
    private val viewed = AtomicBoolean(false)
    private val actionClaimed = AtomicBoolean(false)
    private val exposureResolved = CompletableDeferred<Boolean>()

    fun onStepViewed() {
        actionClaimed.set(false)
        if (!viewed.compareAndSet(false, true)) return
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_GOAL_SELECT)
        viewModelScope.launch(persistenceDispatcher) {
            val resolved = runCatching {
                draftStore.markExposedIfNeeded(OnboardingVariant.PromiseCoachV1)
                check(FirstPromiseMilestone.Exposure in draftStore.readState().trackedMilestones)
                check(draftStore.markGoalSelectViewed() !is FirstPromiseStateMutation.Rejected)
            }.isSuccess
            if (resolved) runCatching { onboardingAnalyticsDispatcher.drain() }
            exposureResolved.complete(resolved)
        }
    }

    fun selectGoal(goal: FirstPromiseGoal) = intent {
        if (goal != FirstPromiseGoal.Unspecified) reduce { state.copy(selectedGoal = goal) }
    }

    fun continuePersonalized() = intent {
        if (!actionClaimed.compareAndSet(false, true)) return@intent
        if (!exposureResolved.await()) {
            actionClaimed.set(false)
            return@intent
        }
        val goal = state.selectedGoal ?: run {
            actionClaimed.set(false)
            return@intent
        }
        if (draftStore.choosePersonalizedGoal(goal) is FirstPromiseStateMutation.Changed) {
            runCatching { onboardingAnalyticsDispatcher.drain() }
            postSideEffect(GoalSelectSideEffect.NavigateUsageAccess)
        } else {
            actionClaimed.set(false)
        }
    }

    fun chooseManual() = intent {
        if (!actionClaimed.compareAndSet(false, true)) return@intent
        if (!exposureResolved.await()) {
            actionClaimed.set(false)
            return@intent
        }
        if (draftStore.chooseManualGoal() is FirstPromiseStateMutation.Changed) {
            FirstPromiseAnalysisTransientHolder.clear()
            runCatching { onboardingAnalyticsDispatcher.drain() }
            postSideEffect(GoalSelectSideEffect.NavigateManualAppSelect)
        } else {
            actionClaimed.set(false)
        }
    }
}

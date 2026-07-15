package com.uiery.keep.feature.onboarding.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
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
) : ViewModel(), ContainerHost<GoalSelectUiState, GoalSelectSideEffect> {
    @Inject constructor(
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore,
    ) : this(analytics, draftStore, Dispatchers.IO)

    override val container: Container<GoalSelectUiState, GoalSelectSideEffect> = container(GoalSelectUiState())
    private val viewed = AtomicBoolean(false)
    private val completed = AtomicBoolean(false)
    private val exposureResolved = CompletableDeferred<Boolean>()

    fun onStepViewed() {
        if (!viewed.compareAndSet(false, true)) return
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_GOAL_SELECT)
        analytics.trackOnboardingStepView(OnboardingStepName.GOAL_SELECT)
        viewModelScope.launch(persistenceDispatcher) {
            val resolved = runCatching {
                if (draftStore.markExposedIfNeeded(OnboardingVariant.PromiseCoachV1)) {
                    analytics.trackOnboardingExperimentExposed(
                        OnboardingVariant.PromiseCoachV1,
                        OnboardingAssignmentVersion.V1,
                    )
                }
            }.isSuccess
            exposureResolved.complete(resolved)
        }
    }

    fun selectGoal(goal: FirstPromiseGoal) = intent {
        if (goal != FirstPromiseGoal.Unspecified) reduce { state.copy(selectedGoal = goal) }
    }

    fun continuePersonalized() = intent {
        if (!exposureResolved.await()) return@intent
        val goal = state.selectedGoal ?: return@intent
        val selected = draftStore.selectGoal(goal, FirstPromisePath.Personalized)
        val advanced = draftStore.advanceToUsageAccess()
        if (selected !is FirstPromiseStateMutation.Rejected && advanced is FirstPromiseStateMutation.Changed) {
            completeOnce()
            postSideEffect(GoalSelectSideEffect.NavigateUsageAccess)
        }
    }

    fun chooseManual() = intent {
        if (!exposureResolved.await()) return@intent
        val selected = draftStore.selectGoal(FirstPromiseGoal.Unspecified, FirstPromisePath.Manual)
        val advanced = draftStore.chooseManualSetup()
        if (selected !is FirstPromiseStateMutation.Rejected && advanced is FirstPromiseStateMutation.Changed) {
            completeOnce()
            postSideEffect(GoalSelectSideEffect.NavigateManualAppSelect)
        }
    }

    private fun completeOnce() {
        if (completed.compareAndSet(false, true)) {
            analytics.trackOnboardingStepComplete(OnboardingStepName.GOAL_SELECT)
        }
    }
}

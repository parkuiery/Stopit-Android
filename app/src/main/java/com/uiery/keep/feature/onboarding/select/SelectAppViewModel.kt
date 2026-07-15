package com.uiery.keep.feature.onboarding.select

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.FirstPromiseRecommendationPolicy
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class SelectAppViewModel internal constructor(
    private val blockingStateStore: BlockingStateStore,
    private val analytics: KeepAnalytics,
    private val draftStore: FirstPromiseDraftStore?,
    private val draftId: () -> String,
) : ContainerHost<SelectAppUiState, SelectAppSideEffect>, ViewModel() {
    @Inject constructor(
        blockingStateStore: BlockingStateStore,
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore,
    ) : this(blockingStateStore, analytics, draftStore, { UUID.randomUUID().toString() })

    constructor(
        blockingStateStore: BlockingStateStore,
        analytics: KeepAnalytics,
    ) : this(blockingStateStore, analytics, null, { UUID.randomUUID().toString() })

    override val container: Container<SelectAppUiState, SelectAppSideEffect> =
        container(SelectAppUiState())

    fun onStepViewed() {
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_SELECT_APP)
        analytics.trackOnboardingStepView(OnboardingStepName.SELECT_APP)
    }

    internal fun showCategoryBottomSheet() = intent {
        reduce { state.copy(isShowCategoryBottomSheet = true) }
    }

    internal fun hideCategoryBottomSheet() = intent {
        reduce { state.copy(isShowCategoryBottomSheet = false) }
    }

    internal fun selectCategoryComplete(selectedAppPackage: Set<String>) = intent {
        if (!canCompleteOnboardingAppSelection(selectedAppPackage)) return@intent

        analytics.trackAppSelectionCompleted(
            selectedAppCount = selectedAppPackage.size,
            isOnboarding = true,
        )
        analytics.trackOnboardingStepComplete(OnboardingStepName.SELECT_APP)
        trackFirstLockConfiguredIfNeeded(selectedAppPackage = selectedAppPackage)
        storeSelectedApp(selectedAppPackage)
        storeIsNew()
    }

    internal fun selectManualCategoryComplete(
        packageName: String,
        appLabel: String,
    ) = intent {
        val store = draftStore ?: return@intent
        val current = runCatching { store.readState() }.getOrNull() ?: return@intent
        val proposal = runCatching {
            FirstPromiseRecommendationPolicy.fromSelection(
                draftId = draftId(),
                goal = current.goal,
                packageName = packageName,
                appLabel = appLabel,
            )
        }.getOrNull() ?: return@intent
        val mutation = store.createManualDraft(
            draft = proposal.draft,
            reason = FirstPromiseRecommendationPolicy.toReasonRef(proposal),
        )
        if (mutation !is FirstPromiseStateMutation.Changed) return@intent

        if (FirstPromiseMilestone.AppSelection !in current.trackedMilestones) {
            analytics.trackAppSelectionCompleted(selectedAppCount = 1, isOnboarding = true)
            analytics.trackOnboardingStepComplete(OnboardingStepName.SELECT_APP)
        }
        postSideEffect(SelectAppSideEffect.NavigateProposal)
    }

    private fun storeSelectedApp(selectedAppPackage: Set<String>) = intent {
        blockingStateStore.saveSelectedAppPackages(selectedAppPackage)
    }

    private fun storeIsNew() = intent {
        blockingStateStore.setIsNew(false)
    }

    private suspend fun trackFirstLockConfiguredIfNeeded(selectedAppPackage: Set<String>) {
        if (!blockingStateStore.markFirstLockConfiguredIfNeeded()) return

        analytics.trackFirstLockConfigured(
            source = AnalyticsSource.ONBOARDING,
            selectedAppCount = selectedAppPackage.size,
        )
    }
}

data class SelectAppUiState(
    val isShowCategoryBottomSheet: Boolean = false,
)

sealed interface SelectAppSideEffect {
    data object NavigateProposal : SelectAppSideEffect
}

internal fun canCompleteOnboardingAppSelection(selectedAppPackages: Set<String>): Boolean =
    selectedAppPackages.isNotEmpty()

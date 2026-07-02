package com.uiery.keep.feature.onboarding.select

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.BlockingStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class SelectAppViewModel @Inject constructor(
    private val blockingStateStore: BlockingStateStore,
    private val analytics: KeepAnalytics,
) : ContainerHost<SelectAppUiState, SelectAppSideEffect>, ViewModel() {
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

sealed class SelectAppSideEffect

internal fun canCompleteOnboardingAppSelection(selectedAppPackages: Set<String>): Boolean =
    selectedAppPackages.isNotEmpty()

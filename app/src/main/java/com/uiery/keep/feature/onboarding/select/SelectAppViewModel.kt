package com.uiery.keep.feature.onboarding.select

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.PreferencesKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class SelectAppViewModel @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val analytics: KeepAnalytics,
) : ContainerHost<SelectAppUiState, SelectAppSideEffect>, ViewModel() {
    override val container: Container<SelectAppUiState, SelectAppSideEffect> =
        container(SelectAppUiState())

    fun onStepViewed() {
        analytics.trackOnboardingStepView(OnboardingStepName.SELECT_APP)
    }

    internal fun showCategoryBottomSheet() = intent {
        reduce { state.copy(isShowCategoryBottomSheet = true) }
    }

    internal fun hideCategoryBottomSheet() = intent {
        reduce { state.copy(isShowCategoryBottomSheet = false) }
    }

    internal fun selectCategoryComplete(selectedAppPackage: Set<String>) = intent {
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
        dataStore.edit { preferences ->
            preferences[PreferencesKey.SELECTED_APP_PACKAGES] = selectedAppPackage
        }
    }

    private fun storeIsNew() = intent {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.IS_NEW] = false
        }
    }

    private suspend fun trackFirstLockConfiguredIfNeeded(selectedAppPackage: Set<String>) {
        val hasTracked =
            dataStore.data
                .map { preferences ->
                    preferences[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] == true
                }.firstOrNull() == true

        if (hasTracked) return

        analytics.trackFirstLockConfigured(
            source = AnalyticsSource.ONBOARDING,
            selectedAppCount = selectedAppPackage.size,
        )

        dataStore.edit { preferences ->
            preferences[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] = true
        }
    }
}

data class SelectAppUiState(
    val isShowCategoryBottomSheet: Boolean = false,
)

sealed class SelectAppSideEffect

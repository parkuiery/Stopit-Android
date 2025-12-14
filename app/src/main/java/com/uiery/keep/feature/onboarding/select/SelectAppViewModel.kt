package com.uiery.keep.feature.onboarding.select

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.PreferencesKey
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class SelectAppViewModel @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val analytics: FirebaseAnalytics,
) : ContainerHost<SelectAppUiState, SelectAppSideEffect>, ViewModel() {
    override val container: Container<SelectAppUiState, SelectAppSideEffect> =
        container(SelectAppUiState())

    internal fun showCategoryBottomSheet() = intent {
        selectAppAnalytics()
        reduce { state.copy(isShowCategoryBottomSheet = true) }
    }

    internal fun hideCategoryBottomSheet() = intent {
        reduce { state.copy(isShowCategoryBottomSheet = false)}
    }

    internal fun selectCategoryComplete(selectedAppPackage: Set<String>) = intent {
        selectAppCompleteAnalytics(selectedAppPackage = selectedAppPackage)
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

    private fun selectAppAnalytics() {
        analytics.logEvent("select_app_click", null)
    }

    private fun selectAppCompleteAnalytics(selectedAppPackage: Set<String>) {
        analytics.logEvent("select_app_complete") {
            param("selected_app_package_size", selectedAppPackage.size.toLong())
            param("selected_app_package", selectedAppPackage.joinToString(", ") { it })
        }
    }
}

data class SelectAppUiState(
    val isShowCategoryBottomSheet: Boolean = false,
)

sealed class SelectAppSideEffect
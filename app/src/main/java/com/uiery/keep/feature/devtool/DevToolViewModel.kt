package com.uiery.keep.feature.devtool

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.PreferencesKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class DevToolViewModel @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val analytics: KeepAnalytics,
) : ContainerHost<DevToolUiState,DevToolSideEffect>, ViewModel() {

    override val container: Container<DevToolUiState, DevToolSideEffect> = container(DevToolUiState())

    init {
        analytics.logScreenView(KeepAnalyticsScreen.DEV_TOOL)
        getFcmToken()
    }

    private fun getFcmToken() = intent {
        val fcmToken = dataStore.data.map { preferences ->
            preferences[PreferencesKey.FCM_TOKEN]
        }.firstOrNull()
        reduce { state.copy(fcmToken = fcmToken ?: "") }
    }
}

data class DevToolUiState(
    val fcmToken: String = "",
)

sealed class DevToolSideEffect
package com.uiery.keep.feature.lockhistory.blockedapps

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.feature.lockhistory.LockHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class BlockedAppsViewModel @Inject constructor(
    private val lockHistoryRepository: LockHistoryRepository,
    private val analytics: KeepAnalytics,
) : ContainerHost<BlockedAppsUiState, BlockedAppsSideEffect>, ViewModel() {

    override val container: Container<BlockedAppsUiState, BlockedAppsSideEffect> =
        container(BlockedAppsUiState())

    init {
        analytics.logScreenView(KeepAnalyticsScreen.BLOCKED_APPS)
        loadBlockedApps()
    }

    private fun loadBlockedApps() = intent {
        val blockedApps = lockHistoryRepository.blockedAppsByFrequency()
            .firstOrNull()
            ?: emptyList()

        reduce {
            state.copy(blockedApps = blockedApps)
        }
    }
}

data class BlockedAppsUiState(
    val blockedApps: List<Pair<String, Int>> = emptyList(),
)

sealed class BlockedAppsSideEffect

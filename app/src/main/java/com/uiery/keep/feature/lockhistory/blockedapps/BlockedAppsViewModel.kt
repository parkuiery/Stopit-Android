package com.uiery.keep.feature.lockhistory.blockedapps

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.model.toModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class BlockedAppsViewModel @Inject constructor(
    private val lockHistoryDao: LockHistoryDao,
    private val analytics: KeepAnalytics,
) : ContainerHost<BlockedAppsUiState, BlockedAppsSideEffect>, ViewModel() {

    override val container: Container<BlockedAppsUiState, BlockedAppsSideEffect> =
        container(BlockedAppsUiState())

    init {
        analytics.logScreenView(KeepAnalyticsScreen.BLOCKED_APPS)
        loadBlockedApps()
    }

    private fun loadBlockedApps() = intent {
        val sessions = lockHistoryDao.fetchAll()
            .firstOrNull()
            ?.map { it.toModel() }
            ?: emptyList()

        val blockedApps = sessions
            .flatMap { it.lockedApps }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        reduce {
            state.copy(blockedApps = blockedApps)
        }
    }
}

data class BlockedAppsUiState(
    val blockedApps: List<Pair<String, Int>> = emptyList(),
)

sealed class BlockedAppsSideEffect

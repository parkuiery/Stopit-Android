package com.uiery.keep.feature.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.model.toModel
import com.uiery.keep.service.summarizeLockHistoryLedger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val analytics: KeepAnalytics,
    private val lockHistoryDao: LockHistoryDao,
) : ContainerHost<HistoryUiState, HistorySideEffect>, ViewModel() {
    override val container: Container<HistoryUiState, HistorySideEffect> =
        container(HistoryUiState())

    init {
        analytics.logScreenView(KeepAnalyticsScreen.HISTORY)
        getBlockTime()
    }

    private fun getBlockTime() = intent {
        val sessions = lockHistoryDao.fetchAll().firstOrNull()?.map { it.toModel() }.orEmpty()

        val (longBlockTime, totalBlockTime) = if (sessions.isNotEmpty()) {
            val summary = summarizeLockHistoryLedger(sessions)
            summary.longestDurationMillis to summary.totalDurationMillis
        } else {
            val legacyLongBlockTime = dataStore.data.map { data ->
                data[PreferencesKey.LONG_BLOCK_TIME] ?: 0L
            }.firstOrNull() ?: 0L

            val legacyTotalBlockTime = dataStore.data.map { data ->
                data[PreferencesKey.TOTAL_BLOCK_TIME] ?: 0L
            }.firstOrNull() ?: 0L
            legacyLongBlockTime to legacyTotalBlockTime
        }

        reduce { state.copy(totalBlockTime = totalBlockTime, longBlockTime = longBlockTime) }
    }
}

data class HistoryUiState(
    val totalBlockTime: Long = 0,
    val longBlockTime: Long = 0,
)

sealed class HistorySideEffect
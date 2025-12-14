package com.uiery.keep.feature.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.PreferencesKey
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
) : ContainerHost<HistoryUiState, HistorySideEffect>, ViewModel() {
    override val container: Container<HistoryUiState, HistorySideEffect> =
        container(HistoryUiState())

    init {
        getBlockTime()
    }

    private fun getBlockTime() = intent {
        val longBlockTime = dataStore.data.map { data ->
            data[PreferencesKey.LONG_BLOCK_TIME] ?: 0L
        }.firstOrNull() ?: 0L

        val totalBlockTime = dataStore.data.map { data ->
            data[PreferencesKey.TOTAL_BLOCK_TIME] ?: 0L
        }.firstOrNull() ?: 0L

        reduce { state.copy(totalBlockTime = totalBlockTime, longBlockTime = longBlockTime) }
    }
}

data class HistoryUiState(
    val totalBlockTime: Long = 0,
    val longBlockTime: Long = 0,
)

sealed class HistorySideEffect
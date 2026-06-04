package com.uiery.keep.feature.emergencyunlocksettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import com.uiery.keep.service.ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.MAX_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.MIN_EMERGENCY_UNLOCK_DAILY_LIMIT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmergencyUnlockSettingsViewModel
    @Inject
    constructor(
        private val settingsStore: EmergencyUnlockSettingsStore,
        private val analytics: KeepAnalytics,
    ) : ViewModel() {
        val uiState: StateFlow<EmergencyUnlockSettingsUiState> =
            settingsStore.settings
                .map { settings ->
                    EmergencyUnlockSettingsUiState(
                        enabled = settings.enabled,
                        dailyLimit = settings.dailyLimit,
                        durationOptions = settings.durationOptions.toSet(),
                        reasonRequired = settings.reasonRequired,
                        autoResetEnabled = settings.autoResetEnabled,
                        manualResetAtMillis = settings.manualResetAtMillis,
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = EmergencyUnlockSettingsUiState(),
                )

        init {
            analytics.logScreenView(KeepAnalyticsScreen.EMERGENCY_UNLOCK_SETTINGS)
        }

        fun setEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsStore.setEnabled(enabled)
            }
        }

        fun setDailyLimit(limit: Int) {
            viewModelScope.launch {
                settingsStore.setDailyLimit(limit)
            }
        }

        fun toggleDuration(minutes: Int) {
            if (minutes !in ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS) return
            viewModelScope.launch {
                settingsStore.toggleDuration(minutes)
            }
        }

        fun setReasonRequired(required: Boolean) {
            viewModelScope.launch {
                settingsStore.setReasonRequired(required)
            }
        }

        fun setAutoResetEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsStore.setAutoResetEnabled(enabled)
            }
        }

        fun markManualReset() {
            viewModelScope.launch {
                settingsStore.markManualReset()
            }
        }
    }

data class EmergencyUnlockSettingsUiState(
    val enabled: Boolean = true,
    val dailyLimit: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val durationOptions: Set<Int> = DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS.toSet(),
    val reasonRequired: Boolean = true,
    val autoResetEnabled: Boolean = true,
    val manualResetAtMillis: Long = 0L,
    val allowedDailyLimits: IntRange = MIN_EMERGENCY_UNLOCK_DAILY_LIMIT..MAX_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val allowedDurations: List<Int> = ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS,
)

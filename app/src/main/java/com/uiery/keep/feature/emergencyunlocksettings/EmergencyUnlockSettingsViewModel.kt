package com.uiery.keep.feature.emergencyunlocksettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockDurationCountBucket
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockManualResetResult
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockRefillMode
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockRemainingUnlocksBucket
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockSettingName
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockSettingsValueBucket
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import com.uiery.keep.service.ALLOWED_EMERGENCY_UNLOCK_COUNTDOWN_OPTIONS
import com.uiery.keep.service.ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_ENABLED
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_SECONDS
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.service.MAX_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.MIN_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.sanitizeEmergencyUnlockCountdownSeconds
import com.uiery.keep.service.sanitizeEmergencyUnlockDailyLimit
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
        private val emergencyUnlockCoordinator: EmergencyUnlockCoordinator,
        private val analytics: KeepAnalytics,
    ) : ViewModel() {
        val uiState: StateFlow<EmergencyUnlockSettingsUiState> =
            settingsStore.settings
                .map { settings ->
                    val availability = emergencyUnlockCoordinator.readAvailability()
                    EmergencyUnlockSettingsUiState(
                        enabled = settings.enabled,
                        dailyLimit = settings.dailyLimit,
                        durationOptions = settings.durationOptions.toSet(),
                        reasonRequired = settings.reasonRequired,
                        autoResetEnabled = settings.autoResetEnabled,
                        manualResetAtMillis = settings.manualResetAtMillis,
                        countdownEnabled = settings.countdownEnabled,
                        countdownSeconds = settings.countdownSeconds,
                        refillMode = EmergencyUnlockRefillMode.fromAutoResetEnabled(settings.autoResetEnabled),
                        remainingUnlockCount = availability.dailyUnlockRemaining,
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
                applyEnabled(enabled)
            }
        }

        internal suspend fun applyEnabled(enabled: Boolean) {
            if (settingsStore.readSettings().enabled == enabled) return
            settingsStore.setEnabled(enabled)
            analytics.trackEmergencyUnlockSettingsChanged(
                settingName = AnalyticsEmergencyUnlockSettingName.ENABLED,
                valueBucket = onOffBucket(enabled),
                refillMode = AnalyticsEmergencyUnlockRefillMode.NOT_APPLICABLE,
                durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.NOT_APPLICABLE,
                source = AnalyticsSource.MENU,
            )
        }

        fun setDailyLimit(limit: Int) {
            viewModelScope.launch {
                applyDailyLimit(limit)
            }
        }

        internal suspend fun applyDailyLimit(limit: Int) {
            val sanitizedLimit = sanitizeEmergencyUnlockDailyLimit(limit)
            if (settingsStore.readSettings().dailyLimit == sanitizedLimit) return
            settingsStore.setDailyLimit(limit)
            analytics.trackEmergencyUnlockSettingsChanged(
                settingName = AnalyticsEmergencyUnlockSettingName.DAILY_LIMIT,
                valueBucket = dailyLimitBucket(sanitizedLimit),
                refillMode = AnalyticsEmergencyUnlockRefillMode.NOT_APPLICABLE,
                durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.NOT_APPLICABLE,
                source = AnalyticsSource.MENU,
            )
        }

        fun toggleDuration(minutes: Int) {
            if (minutes !in ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS) return
            viewModelScope.launch {
                applyDurationToggle(minutes)
            }
        }

        internal suspend fun applyDurationToggle(minutes: Int) {
            if (minutes !in ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS) return
            val before = settingsStore.readSettings().durationOptions
            settingsStore.toggleDuration(minutes)
            val settings = settingsStore.readSettings()
            if (settings.durationOptions == before) return
            analytics.trackEmergencyUnlockSettingsChanged(
                settingName = AnalyticsEmergencyUnlockSettingName.DURATION_OPTIONS,
                valueBucket = durationOptionsBucket(settings.durationOptions),
                refillMode = AnalyticsEmergencyUnlockRefillMode.NOT_APPLICABLE,
                durationCountBucket = durationCountBucket(settings.durationOptions.size),
                source = AnalyticsSource.MENU,
            )
        }

        fun setCountdownEnabled(enabled: Boolean) {
            viewModelScope.launch {
                applyCountdownEnabled(enabled)
            }
        }

        internal suspend fun applyCountdownEnabled(enabled: Boolean) {
            if (settingsStore.readSettings().countdownEnabled == enabled) return
            settingsStore.setCountdownEnabled(enabled)
            analytics.trackEmergencyUnlockSettingsChanged(
                settingName = AnalyticsEmergencyUnlockSettingName.COUNTDOWN,
                valueBucket = onOffBucket(enabled),
                refillMode = AnalyticsEmergencyUnlockRefillMode.NOT_APPLICABLE,
                durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.NOT_APPLICABLE,
                source = AnalyticsSource.MENU,
            )
        }

        fun setCountdownSeconds(seconds: Int) {
            if (seconds !in ALLOWED_EMERGENCY_UNLOCK_COUNTDOWN_OPTIONS) return
            viewModelScope.launch {
                applyCountdownSeconds(seconds)
            }
        }

        internal suspend fun applyCountdownSeconds(seconds: Int) {
            val sanitized = sanitizeEmergencyUnlockCountdownSeconds(seconds)
            if (settingsStore.readSettings().countdownSeconds == sanitized) return
            settingsStore.setCountdownSeconds(sanitized)
            analytics.trackEmergencyUnlockSettingsChanged(
                settingName = AnalyticsEmergencyUnlockSettingName.COUNTDOWN_SECONDS,
                valueBucket = countdownSecondsBucket(sanitized),
                refillMode = AnalyticsEmergencyUnlockRefillMode.NOT_APPLICABLE,
                durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.NOT_APPLICABLE,
                source = AnalyticsSource.MENU,
            )
        }

        fun setReasonRequired(required: Boolean) {
            viewModelScope.launch {
                applyReasonRequired(required)
            }
        }

        internal suspend fun applyReasonRequired(required: Boolean) {
            if (settingsStore.readSettings().reasonRequired == required) return
            settingsStore.setReasonRequired(required)
            analytics.trackEmergencyUnlockSettingsChanged(
                settingName = AnalyticsEmergencyUnlockSettingName.REASON_REQUIRED,
                valueBucket = onOffBucket(required),
                refillMode = AnalyticsEmergencyUnlockRefillMode.NOT_APPLICABLE,
                durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.NOT_APPLICABLE,
                source = AnalyticsSource.MENU,
            )
        }

        fun setAutoResetEnabled(enabled: Boolean) {
            viewModelScope.launch {
                applyAutoResetEnabled(enabled)
            }
        }

        internal suspend fun applyAutoResetEnabled(enabled: Boolean) {
            if (settingsStore.readSettings().autoResetEnabled == enabled) return
            settingsStore.setAutoResetEnabled(enabled)
            val refillMode = refillModeBucket(enabled)
            analytics.trackEmergencyUnlockSettingsChanged(
                settingName = AnalyticsEmergencyUnlockSettingName.REFILL_MODE,
                valueBucket = refillMode,
                refillMode = refillMode,
                durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.NOT_APPLICABLE,
                source = AnalyticsSource.MENU,
            )
        }

        fun setRefillMode(mode: EmergencyUnlockRefillMode) {
            setAutoResetEnabled(mode.autoResetEnabled)
        }

        fun markManualReset() {
            viewModelScope.launch {
                applyManualReset()
            }
        }

        internal suspend fun applyManualReset() {
            if (settingsStore.readSettings().autoResetEnabled) return
            val availability = emergencyUnlockCoordinator.readAvailability()
            settingsStore.markManualReset()
            analytics.trackEmergencyUnlockManualResetRequested(
                remainingUnlocksBucket = remainingUnlocksBucket(availability.dailyUnlockRemaining),
                source = AnalyticsSource.MENU,
                resetResult = AnalyticsEmergencyUnlockManualResetResult.COMPLETED,
            )
        }

        private fun onOffBucket(enabled: Boolean): String =
            if (enabled) AnalyticsEmergencyUnlockSettingsValueBucket.ON else AnalyticsEmergencyUnlockSettingsValueBucket.OFF

        private fun dailyLimitBucket(limit: Int): String =
            when (limit.coerceAtLeast(0)) {
                1 -> AnalyticsEmergencyUnlockSettingsValueBucket.ONE
                2 -> AnalyticsEmergencyUnlockSettingsValueBucket.TWO
                3 -> AnalyticsEmergencyUnlockSettingsValueBucket.THREE
                else -> AnalyticsEmergencyUnlockSettingsValueBucket.FOUR_PLUS
            }

        private fun countdownSecondsBucket(seconds: Int): String =
            when (seconds) {
                10 -> AnalyticsEmergencyUnlockSettingsValueBucket.SECONDS_10
                60 -> AnalyticsEmergencyUnlockSettingsValueBucket.SECONDS_60
                else -> AnalyticsEmergencyUnlockSettingsValueBucket.SECONDS_30
            }

        private fun durationOptionsBucket(options: List<Int>): String =
            when {
                options.isEmpty() -> AnalyticsEmergencyUnlockSettingsValueBucket.NONE
                options.any { it >= 15 } -> AnalyticsEmergencyUnlockSettingsValueBucket.LONG_INCLUDED
                options.all { it <= 5 } -> AnalyticsEmergencyUnlockSettingsValueBucket.SHORT_ONLY
                else -> AnalyticsEmergencyUnlockSettingsValueBucket.MIXED
            }

        private fun durationCountBucket(count: Int): String =
            when (count) {
                0 -> AnalyticsEmergencyUnlockDurationCountBucket.ZERO
                1 -> AnalyticsEmergencyUnlockDurationCountBucket.ONE
                2, 3 -> AnalyticsEmergencyUnlockDurationCountBucket.TWO_TO_THREE
                else -> AnalyticsEmergencyUnlockDurationCountBucket.FOUR_PLUS
            }

        private fun refillModeBucket(autoResetEnabled: Boolean): String =
            if (autoResetEnabled) AnalyticsEmergencyUnlockRefillMode.DAILY else AnalyticsEmergencyUnlockRefillMode.MANUAL

        private fun remainingUnlocksBucket(remaining: Int): String =
            when (remaining) {
                0 -> AnalyticsEmergencyUnlockRemainingUnlocksBucket.ZERO
                1 -> AnalyticsEmergencyUnlockRemainingUnlocksBucket.ONE
                2 -> AnalyticsEmergencyUnlockRemainingUnlocksBucket.TWO
                in 3..Int.MAX_VALUE -> AnalyticsEmergencyUnlockRemainingUnlocksBucket.THREE_PLUS
                else -> AnalyticsEmergencyUnlockRemainingUnlocksBucket.UNKNOWN
            }
    }

data class EmergencyUnlockSettingsUiState(
    val enabled: Boolean = true,
    val dailyLimit: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val durationOptions: Set<Int> = DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS.toSet(),
    val reasonRequired: Boolean = true,
    val autoResetEnabled: Boolean = true,
    val manualResetAtMillis: Long = 0L,
    val countdownEnabled: Boolean = DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_ENABLED,
    val countdownSeconds: Int = DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_SECONDS,
    val refillMode: EmergencyUnlockRefillMode = EmergencyUnlockRefillMode.Daily,
    val remainingUnlockCount: Int = dailyLimit,
    val allowedDailyLimits: IntRange = MIN_EMERGENCY_UNLOCK_DAILY_LIMIT..MAX_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val allowedDurations: List<Int> = ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS,
    val allowedCountdownSeconds: List<Int> = ALLOWED_EMERGENCY_UNLOCK_COUNTDOWN_OPTIONS,
)

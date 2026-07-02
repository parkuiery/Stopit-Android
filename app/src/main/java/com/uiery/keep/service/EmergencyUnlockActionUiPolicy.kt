package com.uiery.keep.service

import androidx.annotation.StringRes
import com.uiery.keep.R

internal data class EmergencyUnlockActionUiState(
    val enabled: Boolean,
    @StringRes val textRes: Int,
    @StringRes val helperTextRes: Int,
)

internal fun emergencyUnlockActionUiState(
    reason: EmergencyUnlockAvailabilityReason,
): EmergencyUnlockActionUiState =
    when (reason) {
        EmergencyUnlockAvailabilityReason.Available -> EmergencyUnlockActionUiState(
            enabled = true,
            textRes = R.string.emergency_unlock_with_count,
            helperTextRes = R.string.emergency_unlock_available_helper,
        )
        EmergencyUnlockAvailabilityReason.Disabled -> EmergencyUnlockActionUiState(
            enabled = false,
            textRes = R.string.emergency_unlock_disabled,
            helperTextRes = R.string.emergency_unlock_disabled_helper,
        )
        EmergencyUnlockAvailabilityReason.DailyLimitZero -> EmergencyUnlockActionUiState(
            enabled = false,
            textRes = R.string.emergency_unlock_daily_limit_zero,
            helperTextRes = R.string.emergency_unlock_daily_limit_zero_helper,
        )
        EmergencyUnlockAvailabilityReason.DailyLimitExhausted -> EmergencyUnlockActionUiState(
            enabled = false,
            textRes = R.string.emergency_unlock_daily_limit_reached,
            helperTextRes = R.string.emergency_unlock_daily_limit_reached_helper,
        )
    }

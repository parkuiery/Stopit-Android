package com.uiery.keep.service

import androidx.annotation.StringRes
import com.uiery.keep.R

internal data class EmergencyUnlockActionUiState(
    val enabled: Boolean,
    @StringRes val textRes: Int,
)

internal fun emergencyUnlockActionUiState(
    reason: EmergencyUnlockAvailabilityReason,
): EmergencyUnlockActionUiState =
    when (reason) {
        EmergencyUnlockAvailabilityReason.Available -> EmergencyUnlockActionUiState(
            enabled = true,
            textRes = R.string.emergency_unlock_with_count,
        )
        EmergencyUnlockAvailabilityReason.Disabled -> EmergencyUnlockActionUiState(
            enabled = false,
            textRes = R.string.emergency_unlock_disabled,
        )
        EmergencyUnlockAvailabilityReason.DailyLimitZero -> EmergencyUnlockActionUiState(
            enabled = false,
            textRes = R.string.emergency_unlock_daily_limit_zero,
        )
        EmergencyUnlockAvailabilityReason.DailyLimitExhausted -> EmergencyUnlockActionUiState(
            enabled = false,
            textRes = R.string.emergency_unlock_daily_limit_reached,
        )
    }

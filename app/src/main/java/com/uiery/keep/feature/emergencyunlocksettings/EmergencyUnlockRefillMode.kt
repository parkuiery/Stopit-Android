package com.uiery.keep.feature.emergencyunlocksettings

enum class EmergencyUnlockRefillMode(val autoResetEnabled: Boolean) {
    Daily(autoResetEnabled = true),
    Manual(autoResetEnabled = false),
    ;

    companion object {
        fun fromAutoResetEnabled(autoResetEnabled: Boolean): EmergencyUnlockRefillMode =
            if (autoResetEnabled) Daily else Manual
    }
}

package com.uiery.keep.feature.emergencyunlocksettings

import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyUnlockRefillModeTest {
    @Test
    fun dailyRefillModeMapsFromAutoResetEnabled() {
        val mode = EmergencyUnlockRefillMode.fromAutoResetEnabled(autoResetEnabled = true)

        assertEquals(EmergencyUnlockRefillMode.Daily, mode)
        assertEquals(true, mode.autoResetEnabled)
    }

    @Test
    fun manualRefillModeMapsFromAutoResetDisabled() {
        val mode = EmergencyUnlockRefillMode.fromAutoResetEnabled(autoResetEnabled = false)

        assertEquals(EmergencyUnlockRefillMode.Manual, mode)
        assertEquals(false, mode.autoResetEnabled)
    }
}

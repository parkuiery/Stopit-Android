package com.uiery.keep.service

import com.uiery.keep.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUnlockActionUiPolicyTest {
    @Test
    fun availableReasonShowsRemainingCountAndEnablesAction() {
        val state = emergencyUnlockActionUiState(EmergencyUnlockAvailabilityReason.Available)

        assertTrue(state.enabled)
        assertEquals(R.string.emergency_unlock_with_count, state.textRes)
    }

    @Test
    fun disabledReasonShowsDisabledCopyInsteadOfDailyLimitReached() {
        val state = emergencyUnlockActionUiState(EmergencyUnlockAvailabilityReason.Disabled)

        assertFalse(state.enabled)
        assertEquals(R.string.emergency_unlock_disabled, state.textRes)
    }

    @Test
    fun zeroDailyLimitReasonIsDistinctFromDailyLimitExhausted() {
        val state = emergencyUnlockActionUiState(EmergencyUnlockAvailabilityReason.DailyLimitZero)

        assertFalse(state.enabled)
        assertEquals(R.string.emergency_unlock_daily_limit_zero, state.textRes)
    }

    @Test
    fun exhaustedReasonKeepsDailyLimitReachedCopy() {
        val state = emergencyUnlockActionUiState(EmergencyUnlockAvailabilityReason.DailyLimitExhausted)

        assertFalse(state.enabled)
        assertEquals(R.string.emergency_unlock_daily_limit_reached, state.textRes)
    }
}

package com.uiery.keep.service

import com.uiery.keep.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUnlockActionUiPolicyTest {
    @Test
    fun availableReasonShowsRemainingCountAndSecondarySafetyContext() {
        val state = emergencyUnlockActionUiState(EmergencyUnlockAvailabilityReason.Available)

        assertTrue(state.enabled)
        assertEquals(R.string.emergency_unlock_with_count, state.textRes)
        assertEquals(R.string.emergency_unlock_available_helper, state.helperTextRes)
    }

    @Test
    fun disabledReasonShowsDisabledCopyAndSecondaryReasonInsteadOfDailyLimitReached() {
        val state = emergencyUnlockActionUiState(EmergencyUnlockAvailabilityReason.Disabled)

        assertFalse(state.enabled)
        assertEquals(R.string.emergency_unlock_disabled, state.textRes)
        assertEquals(R.string.emergency_unlock_disabled_helper, state.helperTextRes)
    }

    @Test
    fun zeroDailyLimitReasonHasDistinctSecondaryReasonFromDailyLimitExhausted() {
        val state = emergencyUnlockActionUiState(EmergencyUnlockAvailabilityReason.DailyLimitZero)

        assertFalse(state.enabled)
        assertEquals(R.string.emergency_unlock_daily_limit_zero, state.textRes)
        assertEquals(R.string.emergency_unlock_daily_limit_zero_helper, state.helperTextRes)
    }

    @Test
    fun exhaustedReasonKeepsDailyLimitReachedCopyAndSecondaryReason() {
        val state = emergencyUnlockActionUiState(EmergencyUnlockAvailabilityReason.DailyLimitExhausted)

        assertFalse(state.enabled)
        assertEquals(R.string.emergency_unlock_daily_limit_reached, state.textRes)
        assertEquals(R.string.emergency_unlock_daily_limit_reached_helper, state.helperTextRes)
    }
}

package com.uiery.keep.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUnlockPolicyTest {

    @Test
    fun dailyLimitIsReachedAtThreeUnlocks() {
        assertFalse(isEmergencyUnlockDailyLimitReached(todayUnlockCount = 2))
        assertTrue(isEmergencyUnlockDailyLimitReached(todayUnlockCount = 3))
        assertTrue(isEmergencyUnlockDailyLimitReached(todayUnlockCount = 4))
    }

    @Test
    fun remainingCountNeverGoesBelowZero() {
        assertEquals(3, emergencyUnlockDailyRemaining(todayUnlockCount = 0))
        assertEquals(1, emergencyUnlockDailyRemaining(todayUnlockCount = 2))
        assertEquals(0, emergencyUnlockDailyRemaining(todayUnlockCount = 3))
        assertEquals(0, emergencyUnlockDailyRemaining(todayUnlockCount = 9))
    }
}

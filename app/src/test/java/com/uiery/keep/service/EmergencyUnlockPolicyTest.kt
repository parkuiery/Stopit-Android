package com.uiery.keep.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUnlockPolicyTest {

    @Test
    fun emergencyUnlockNotificationPostResultRequiresEnabledNotificationsPermissionAndChannel() {
        assertEquals(
            EmergencyUnlockNotificationPostResult.PermissionDenied,
            resolveEmergencyUnlockNotificationPostResult(
                notificationsEnabled = false,
                postNotificationsPermissionGranted = true,
                notificationChannelEnabled = true,
            )
        )
        assertEquals(
            EmergencyUnlockNotificationPostResult.PermissionDenied,
            resolveEmergencyUnlockNotificationPostResult(
                notificationsEnabled = true,
                postNotificationsPermissionGranted = false,
                notificationChannelEnabled = true,
            )
        )
        assertEquals(
            EmergencyUnlockNotificationPostResult.PermissionDenied,
            resolveEmergencyUnlockNotificationPostResult(
                notificationsEnabled = true,
                postNotificationsPermissionGranted = true,
                notificationChannelEnabled = false,
            )
        )
        assertEquals(
            EmergencyUnlockNotificationPostResult.Posted,
            resolveEmergencyUnlockNotificationPostResult(
                notificationsEnabled = true,
                postNotificationsPermissionGranted = true,
                notificationChannelEnabled = true,
            )
        )
    }

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

    @Test
    fun configuredDailyLimitControlsReachedAndRemaining() {
        assertFalse(isEmergencyUnlockDailyLimitReached(dailyLimit = 5, todayUnlockCount = 4))
        assertTrue(isEmergencyUnlockDailyLimitReached(dailyLimit = 5, todayUnlockCount = 5))
        assertEquals(1, emergencyUnlockDailyRemaining(dailyLimit = 5, todayUnlockCount = 4))
        assertEquals(0, emergencyUnlockDailyRemaining(dailyLimit = 5, todayUnlockCount = 9))
    }

    @Test
    fun disabledOrZeroLimitPolicyIsUnavailable() {
        assertFalse(isEmergencyUnlockAvailable(enabled = false, dailyLimit = 3, todayUnlockCount = 0))
        assertFalse(isEmergencyUnlockAvailable(enabled = true, dailyLimit = 0, todayUnlockCount = 0))
        assertTrue(isEmergencyUnlockAvailable(enabled = true, dailyLimit = 3, todayUnlockCount = 2))
    }

    @Test
    fun invalidDailyLimitFallsBackToDefault() {
        assertEquals(3, sanitizeEmergencyUnlockDailyLimit(null))
        assertEquals(3, sanitizeEmergencyUnlockDailyLimit(-1))
        assertEquals(3, sanitizeEmergencyUnlockDailyLimit(6))
        assertEquals(5, sanitizeEmergencyUnlockDailyLimit(5))
    }

    @Test
    fun durationOptionsAreFilteredSortedAndDefaulted() {
        assertEquals(listOf(3, 5, 10), sanitizeEmergencyUnlockDurationOptions(null))
        assertEquals(listOf(3, 5, 10), sanitizeEmergencyUnlockDurationOptions(emptySet()))
        assertEquals(listOf(3, 10), sanitizeEmergencyUnlockDurationOptions(setOf("10", "999", "3", "bad")))
        assertEquals(listOf(3, 5, 10), sanitizeEmergencyUnlockDurationOptions(setOf("999", "bad")))
    }

    @Test
    fun reasonNotRequiredUsesStableReasonKey() {
        assertEquals("not_required", EMERGENCY_UNLOCK_REASON_NOT_REQUIRED)
    }

    @Test
    fun requestCompletionGuardRejectsStaleSettings() {
        val settings = EmergencyUnlockSettings(
            enabled = true,
            dailyLimit = 3,
            durationOptions = listOf(3, 5),
            reasonRequired = true,
        )

        assertTrue(
            canCompleteEmergencyUnlockRequest(
                settings = settings,
                todayUnlockCount = 0,
                durationMinutes = 3,
                reason = "work",
            )
        )
        assertFalse(
            canCompleteEmergencyUnlockRequest(
                settings = settings.copy(enabled = false),
                todayUnlockCount = 0,
                durationMinutes = 3,
                reason = "work",
            )
        )
        assertFalse(
            canCompleteEmergencyUnlockRequest(
                settings = settings,
                todayUnlockCount = 3,
                durationMinutes = 3,
                reason = "work",
            )
        )
        assertFalse(
            canCompleteEmergencyUnlockRequest(
                settings = settings,
                todayUnlockCount = 0,
                durationMinutes = 10,
                reason = "work",
            )
        )
        assertFalse(
            canCompleteEmergencyUnlockRequest(
                settings = settings,
                todayUnlockCount = 0,
                durationMinutes = 3,
                reason = EMERGENCY_UNLOCK_REASON_NOT_REQUIRED,
            )
        )
    }

    @Test
    fun emergencyUnlockOnlyBypassesBeforeExpireTimeForSelectedApp() {
        val packageName = "com.example.blocked"
        val now = 1_000L

        assertTrue(
            isEmergencyUnlockActiveForPackage(
                packageName = packageName,
                unlockedApps = setOf(packageName),
                expireTimeMillis = now + 1,
                nowMillis = now,
            )
        )
        assertFalse(
            isEmergencyUnlockActiveForPackage(
                packageName = packageName,
                unlockedApps = setOf(packageName),
                expireTimeMillis = now,
                nowMillis = now,
            )
        )
        assertFalse(
            isEmergencyUnlockActiveForPackage(
                packageName = packageName,
                unlockedApps = setOf("com.example.other"),
                expireTimeMillis = now + 1,
                nowMillis = now,
            )
        )
    }

    @Test
    fun futureEmergencyUnlockExpiryProducesPositiveDelay() {
        assertEquals(
            500L,
            emergencyUnlockExpiryDelayMillis(
                expireTimeMillis = 1_500L,
                nowMillis = 1_000L,
            )
        )
    }

    @Test
    fun pastEmergencyUnlockExpiryProducesImmediateDelay() {
        assertEquals(
            0L,
            emergencyUnlockExpiryDelayMillis(
                expireTimeMillis = 900L,
                nowMillis = 1_000L,
            )
        )
    }

    @Test
    fun zeroEmergencyUnlockExpiryCancelsScheduling() {
        assertEquals(
            null,
            emergencyUnlockExpiryDelayMillis(
                expireTimeMillis = 0L,
                nowMillis = 1_000L,
            )
        )
    }

    @Test
    fun emergencyUnlockNotificationSyncUsesStoredExpireTimeForRemainingSeconds() {
        assertEquals(
            EmergencyUnlockNotificationSyncPlan.ShowCountdown(remainingSeconds = 90, totalSeconds = 90),
            resolveEmergencyUnlockNotificationSyncPlan(
                expireTimeMillis = 91_000L,
                nowMillis = 1_000L,
            )
        )
    }

    @Test
    fun emergencyUnlockNotificationSyncCancelsWhenNoStoredActiveUnlockRemains() {
        assertEquals(
            EmergencyUnlockNotificationSyncPlan.Cancel,
            resolveEmergencyUnlockNotificationSyncPlan(
                expireTimeMillis = 0L,
                nowMillis = 1_000L,
            )
        )
        assertEquals(
            EmergencyUnlockNotificationSyncPlan.Cancel,
            resolveEmergencyUnlockNotificationSyncPlan(
                expireTimeMillis = 1_000L,
                nowMillis = 1_000L,
            )
        )
    }

    @Test
    fun emergencyUnlockNotificationTickDelayKeepsCountdownAliveUntilStoredExpiry() {
        assertEquals(
            1_000L,
            emergencyUnlockNotificationTickDelayMillis(
                expireTimeMillis = 5_000L,
                nowMillis = 1_000L,
            )
        )
        assertEquals(
            500L,
            emergencyUnlockNotificationTickDelayMillis(
                expireTimeMillis = 1_500L,
                nowMillis = 1_000L,
            )
        )
        assertEquals(
            null,
            emergencyUnlockNotificationTickDelayMillis(
                expireTimeMillis = 1_000L,
                nowMillis = 1_000L,
            )
        )
    }

    @Test
    fun staleEmergencyUnlockExpiryCallbackDoesNotHandle() {
        assertFalse(
            shouldHandleEmergencyUnlockExpiry(
                expectedExpireTimeMillis = 1_000L,
                currentExpireTimeMillis = 2_000L,
                nowMillis = 2_000L,
            )
        )
        assertFalse(
            shouldHandleEmergencyUnlockExpiry(
                expectedExpireTimeMillis = 1_000L,
                currentExpireTimeMillis = 0L,
                nowMillis = 2_000L,
            )
        )
    }

    @Test
    fun currentEmergencyUnlockExpiryCallbackHandlesOnlyAtOrAfterExpiry() {
        assertFalse(
            shouldHandleEmergencyUnlockExpiry(
                expectedExpireTimeMillis = 1_000L,
                currentExpireTimeMillis = 1_000L,
                nowMillis = 999L,
            )
        )
        assertTrue(
            shouldHandleEmergencyUnlockExpiry(
                expectedExpireTimeMillis = 1_000L,
                currentExpireTimeMillis = 1_000L,
                nowMillis = 1_000L,
            )
        )
    }

    @Test
    fun emergencyUnlockExpiryResolutionReblocksExpiredForegroundPackage() {
        assertEquals(
            EmergencyUnlockExpiryResolution(
                shouldClearState = true,
                packageToReblock = "com.example.blocked",
            ),
            resolveEmergencyUnlockExpiry(
                expectedExpireTimeMillis = 1_000L,
                currentExpireTimeMillis = 1_000L,
                expiredUnlockedApps = setOf("com.example.blocked"),
                foregroundPackage = "com.example.blocked",
                applicationId = "com.uiery.keep",
                isForegroundStillEmergencyUnlocked = false,
                nowMillis = 1_000L,
            )
        )
    }
}

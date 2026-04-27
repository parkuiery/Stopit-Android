package com.uiery.keep.service

internal const val DAILY_EMERGENCY_UNLOCK_LIMIT = 3

internal fun isEmergencyUnlockDailyLimitReached(todayUnlockCount: Int): Boolean =
    todayUnlockCount >= DAILY_EMERGENCY_UNLOCK_LIMIT

internal fun emergencyUnlockDailyRemaining(todayUnlockCount: Int): Int =
    (DAILY_EMERGENCY_UNLOCK_LIMIT - todayUnlockCount).coerceAtLeast(0)

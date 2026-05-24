package com.uiery.keep.service

internal const val DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT = 3
internal const val MIN_EMERGENCY_UNLOCK_DAILY_LIMIT = 0
internal const val MAX_EMERGENCY_UNLOCK_DAILY_LIMIT = 5
internal const val EMERGENCY_UNLOCK_REASON_NOT_REQUIRED = "not_required"

internal val ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS = listOf(3, 5, 10, 15)
internal val DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS = listOf(3, 5, 10)

@Deprecated("Use DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT")
internal const val DAILY_EMERGENCY_UNLOCK_LIMIT = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT

internal data class EmergencyUnlockSettings(
    val enabled: Boolean = true,
    val dailyLimit: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val durationOptions: List<Int> = DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS,
    val reasonRequired: Boolean = true,
)

internal fun sanitizeEmergencyUnlockDailyLimit(value: Int?): Int =
    value
        ?.takeIf { it in MIN_EMERGENCY_UNLOCK_DAILY_LIMIT..MAX_EMERGENCY_UNLOCK_DAILY_LIMIT }
        ?: DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT

internal fun sanitizeEmergencyUnlockDurationOptions(values: Set<String>?): List<Int> {
    val sanitized = values
        .orEmpty()
        .mapNotNull { it.toIntOrNull() }
        .filter { it in ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS }
        .distinct()
        .sorted()
    return sanitized.ifEmpty { DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS }
}

internal fun isEmergencyUnlockAvailable(
    enabled: Boolean,
    dailyLimit: Int,
    todayUnlockCount: Int,
): Boolean =
    enabled &&
        dailyLimit > 0 &&
        !isEmergencyUnlockDailyLimitReached(
            dailyLimit = dailyLimit,
            todayUnlockCount = todayUnlockCount,
        )

internal fun canCompleteEmergencyUnlockRequest(
    settings: EmergencyUnlockSettings,
    todayUnlockCount: Int,
    durationMinutes: Int,
    reason: String,
): Boolean =
    isEmergencyUnlockAvailable(
        enabled = settings.enabled,
        dailyLimit = settings.dailyLimit,
        todayUnlockCount = todayUnlockCount,
    ) &&
        durationMinutes in settings.durationOptions &&
        (!settings.reasonRequired || reason != EMERGENCY_UNLOCK_REASON_NOT_REQUIRED)

internal fun isEmergencyUnlockDailyLimitReached(
    dailyLimit: Int,
    todayUnlockCount: Int,
): Boolean =
    todayUnlockCount >= dailyLimit

internal fun isEmergencyUnlockDailyLimitReached(todayUnlockCount: Int): Boolean =
    isEmergencyUnlockDailyLimitReached(
        dailyLimit = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
        todayUnlockCount = todayUnlockCount,
    )

internal fun emergencyUnlockDailyRemaining(
    dailyLimit: Int,
    todayUnlockCount: Int,
): Int =
    (dailyLimit - todayUnlockCount).coerceAtLeast(0)

internal fun emergencyUnlockDailyRemaining(todayUnlockCount: Int): Int =
    emergencyUnlockDailyRemaining(
        dailyLimit = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
        todayUnlockCount = todayUnlockCount,
    )

internal fun isEmergencyUnlockActiveForPackage(
    packageName: String,
    unlockedApps: Set<String>,
    expireTimeMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean =
    unlockedApps.contains(packageName) && nowMillis < expireTimeMillis

internal fun emergencyUnlockExpiryDelayMillis(
    expireTimeMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Long? =
    if (expireTimeMillis > 0L) {
        (expireTimeMillis - nowMillis).coerceAtLeast(0L)
    } else {
        null
    }

internal fun shouldHandleEmergencyUnlockExpiry(
    expectedExpireTimeMillis: Long,
    currentExpireTimeMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean =
    currentExpireTimeMillis > 0L &&
        currentExpireTimeMillis == expectedExpireTimeMillis &&
        nowMillis >= currentExpireTimeMillis

internal data class EmergencyUnlockExpiryResolution(
    val shouldClearState: Boolean,
    val packageToReblock: String? = null,
)

internal fun resolveEmergencyUnlockExpiry(
    expectedExpireTimeMillis: Long,
    currentExpireTimeMillis: Long,
    expiredUnlockedApps: Set<String>,
    foregroundPackage: String?,
    applicationId: String,
    isForegroundStillEmergencyUnlocked: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
): EmergencyUnlockExpiryResolution {
    if (!shouldHandleEmergencyUnlockExpiry(
            expectedExpireTimeMillis = expectedExpireTimeMillis,
            currentExpireTimeMillis = currentExpireTimeMillis,
            nowMillis = nowMillis,
        )) {
        return EmergencyUnlockExpiryResolution(shouldClearState = false)
    }

    val packageToReblock = foregroundPackage
        ?.takeUnless { it == applicationId }
        ?.takeIf { it in expiredUnlockedApps }
        ?.takeUnless { isForegroundStillEmergencyUnlocked }

    return EmergencyUnlockExpiryResolution(
        shouldClearState = true,
        packageToReblock = packageToReblock,
    )
}

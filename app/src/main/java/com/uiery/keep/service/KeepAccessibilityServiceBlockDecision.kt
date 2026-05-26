package com.uiery.keep.service

import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.RoutineRuntimePolicy
import com.uiery.keep.util.isRoutineActiveNow
import com.uiery.keep.util.toDayOfWeekList
import java.time.LocalDateTime

data class AccessibilityBlockingPreferences(
    val isKeep: Boolean = false,
    val lockTime: String? = null,
    val selectedAppPackages: Set<String> = emptySet(),
)

data class ForegroundBlockRequest(
    val packageName: String,
    val blockSource: String,
)

internal fun resolveForegroundBlockRequest(
    packageName: String,
    prefs: AccessibilityBlockingPreferences,
    cachedRoutines: List<RoutineModel>,
    now: LocalDateTime = LocalDateTime.now(),
    isEmergencyUnlocked: Boolean,
    isDuplicateBlock: Boolean,
): ForegroundBlockRequest? {
    if (isEmergencyUnlocked) return null

    val isLockTime = prefs.lockTime?.let {
        runCatching { now.isBefore(LocalDateTime.parse(it)) }.getOrDefault(false)
    } ?: false
    val isShouldRoutineBlock = RoutineRuntimePolicy.shouldBlockPackage(
        packageName = packageName,
        routines = cachedRoutines,
    ) { routine ->
        isRoutineActiveNow(
            startTime = routine.startTime,
            endTime = routine.endTime,
            repeatDays = routine.repeatDays.toDayOfWeekList(),
            nowDateTime = now,
        )
    }
    val isBlocking = prefs.isKeep || isLockTime || isShouldRoutineBlock
    if (!isBlocking) return null
    if (!prefs.selectedAppPackages.contains(packageName) && !isShouldRoutineBlock) return null

    val blockSource = when {
        isShouldRoutineBlock -> AnalyticsBlockSource.ROUTINE
        isLockTime -> AnalyticsBlockSource.TIMED_LOCK
        else -> AnalyticsBlockSource.MANUAL_KEEP
    }
    if (isDuplicateBlock) return null

    return ForegroundBlockRequest(
        packageName = packageName,
        blockSource = blockSource,
    )
}

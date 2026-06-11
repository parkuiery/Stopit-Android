package com.uiery.keep.service

import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockPolicy
import com.uiery.keep.domain.parentmode.ParentModeRuntimePolicy
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.RoutineRuntimePolicy
import com.uiery.keep.util.isRoutineActiveNow
import com.uiery.keep.util.toDayOfWeekList
import java.time.LocalDateTime
import java.time.ZoneId

data class AccessibilityBlockingPreferences(
    val isKeep: Boolean = false,
    val lockTime: String? = null,
    val selectedAppPackages: Set<String> = emptySet(),
)

data class ForegroundBlockRequest(
    val packageName: String,
    val blockSource: String,
    val routineId: String? = null,
    val goalLockId: String? = null,
)

internal fun resolveServiceConnectionForegroundBlockRequest(
    currentForegroundPackage: String?,
    prefs: AccessibilityBlockingPreferences,
    cachedRoutines: List<RoutineModel>,
    cachedGoalLocks: List<GoalLock> = emptyList(),
    parentModeSession: ParentModeSession? = null,
    parentControlPackages: Set<String> = emptySet(),
    now: LocalDateTime = LocalDateTime.now(),
    isEmergencyUnlocked: Boolean,
    isDuplicateBlock: Boolean,
): ForegroundBlockRequest? {
    val packageName = currentForegroundPackage ?: return null
    return resolveForegroundBlockRequest(
        packageName = packageName,
        prefs = prefs,
        cachedRoutines = cachedRoutines,
        cachedGoalLocks = cachedGoalLocks,
        parentModeSession = parentModeSession,
        parentControlPackages = parentControlPackages,
        now = now,
        isEmergencyUnlocked = isEmergencyUnlocked,
        isDuplicateBlock = isDuplicateBlock,
    )
}

internal fun resolveForegroundBlockRequest(
    packageName: String,
    prefs: AccessibilityBlockingPreferences,
    cachedRoutines: List<RoutineModel>,
    cachedGoalLocks: List<GoalLock> = emptyList(),
    parentModeSession: ParentModeSession? = null,
    parentControlPackages: Set<String> = emptySet(),
    now: LocalDateTime = LocalDateTime.now(),
    isEmergencyUnlocked: Boolean,
    isDuplicateBlock: Boolean,
): ForegroundBlockRequest? {
    if (isEmergencyUnlocked) return null
    if (parentModeSession != null && packageName in parentControlPackages) return null

    val isLockTime = ManualLockTimePolicy.isActiveAt(
        storedDeadline = prefs.lockTime,
        now = now.atZone(ZoneId.systemDefault()).toInstant(),
    )
    val blockingRoutine = RoutineRuntimePolicy.findBlockingRoutine(
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
    val isShouldRoutineBlock = blockingRoutine != null
    val blockingGoalLock = cachedGoalLocks.firstOrNull { goalLock ->
        GoalLockPolicy.isBlocking(
            goalLock = goalLock,
            packageName = packageName,
            now = now,
        )
    }
    val isShouldGoalLockBlock = blockingGoalLock != null
    val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val isShouldParentModeBlock = parentModeSession?.let { session ->
        ParentModeRuntimePolicy.shouldBlockPackage(
            session = session,
            packageName = packageName,
            nowMillis = nowMillis,
        )
    } ?: false
    val isBlocking = prefs.isKeep ||
        isLockTime ||
        isShouldRoutineBlock ||
        isShouldGoalLockBlock ||
        isShouldParentModeBlock
    if (!isBlocking) return null
    if (
        !prefs.selectedAppPackages.contains(packageName) &&
        !isShouldRoutineBlock &&
        !isShouldGoalLockBlock &&
        !isShouldParentModeBlock
    ) return null

    val blockSource = when {
        isShouldRoutineBlock -> AnalyticsBlockSource.ROUTINE
        isShouldGoalLockBlock -> AnalyticsBlockSource.GOAL_LOCK
        isShouldParentModeBlock -> AnalyticsBlockSource.PARENT_MODE
        isLockTime -> AnalyticsBlockSource.TIMED_LOCK
        else -> AnalyticsBlockSource.MANUAL_KEEP
    }
    if (isDuplicateBlock) return null

    return ForegroundBlockRequest(
        packageName = packageName,
        blockSource = blockSource,
        routineId = blockingRoutine?.id?.toString(),
        goalLockId = blockingGoalLock?.id?.toString(),
    )
}

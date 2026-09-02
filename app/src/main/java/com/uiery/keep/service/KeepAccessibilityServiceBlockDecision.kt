package com.uiery.keep.service

import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.appselection.BlockExemptPackagePolicy
import com.uiery.keep.appselection.BlockExemptPackages
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockPolicy
import com.uiery.keep.domain.parentmode.ParentModeRuntimePolicy
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.pomodoro.PomodoroPolicy
import com.uiery.keep.domain.pomodoro.PomodoroSession
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
    val exemptPackages: BlockExemptPackages = BlockExemptPackages(),
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
    pomodoroSession: PomodoroSession? = null,
    pomodoroBlockDuringBreaks: Boolean = true,
    parentControlPackages: Set<String> = emptySet(),
    now: LocalDateTime = LocalDateTime.now(),
    isEmergencyUnlocked: Boolean,
    isDuplicateBlock: Boolean,
    isCallInProgress: Boolean = false,
): ForegroundBlockRequest? {
    val packageName = currentForegroundPackage ?: return null
    return resolveForegroundBlockRequest(
        packageName = packageName,
        prefs = prefs,
        cachedRoutines = cachedRoutines,
        cachedGoalLocks = cachedGoalLocks,
        parentModeSession = parentModeSession,
        pomodoroSession = pomodoroSession,
        pomodoroBlockDuringBreaks = pomodoroBlockDuringBreaks,
        parentControlPackages = parentControlPackages,
        now = now,
        isEmergencyUnlocked = isEmergencyUnlocked,
        isDuplicateBlock = isDuplicateBlock,
        isCallInProgress = isCallInProgress,
    )
}

internal fun resolveForegroundBlockRequest(
    packageName: String,
    prefs: AccessibilityBlockingPreferences,
    cachedRoutines: List<RoutineModel>,
    cachedGoalLocks: List<GoalLock> = emptyList(),
    parentModeSession: ParentModeSession? = null,
    pomodoroSession: PomodoroSession? = null,
    pomodoroBlockDuringBreaks: Boolean = true,
    parentControlPackages: Set<String> = emptySet(),
    now: LocalDateTime = LocalDateTime.now(),
    isEmergencyUnlocked: Boolean,
    isDuplicateBlock: Boolean,
    isCallInProgress: Boolean = false,
): ForegroundBlockRequest? {
    // Unconditional: a blocked home launcher leaves no way back to the device.
    if (BlockExemptPackagePolicy.isExempt(packageName, prefs.exemptPackages.homePackages)) return null
    // The user chose to block the dialer, but they did not choose to receive this call. With the
    // screen off the in-call activity comes to the front like any other window, so without this the
    // block screen would land on top of a ringing phone. Placing a call is still blocked — this
    // only steps aside while a call is actually live.
    if (isCallInProgress && BlockExemptPackagePolicy.isExempt(packageName, prefs.exemptPackages.dialerPackages)) {
        return null
    }
    val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    // Parent mode is the one lock the person holding the phone did not agree to, so placing a call
    // cannot depend on the parent having thought to put the dialer on the allowlist. The
    // self-control locks keep blocking outgoing calls above — there the blocker and the caller are
    // the same person, and that trade is theirs to make. It is not theirs to make for a child.
    if (
        ParentModeRuntimePolicy.blockReason(session = parentModeSession, nowMillis = nowMillis) != null &&
        BlockExemptPackagePolicy.isExempt(packageName, prefs.exemptPackages.dialerPackages)
    ) {
        return null
    }
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
    val isShouldParentModeBlock = parentModeSession?.let { session ->
        ParentModeRuntimePolicy.shouldBlockPackage(
            session = session,
            packageName = packageName,
            nowMillis = nowMillis,
        )
    } ?: false
    val nowInstant = now.atZone(ZoneId.systemDefault()).toInstant()
    val isShouldPomodoroBlock = PomodoroPolicy.blocksNow(
        session = pomodoroSession,
        now = nowInstant,
        blockDuringBreaks = pomodoroBlockDuringBreaks,
    )
    // 집중 세션은 사이클 전체 길이의 타이머 잠금 하나를 만들고, 세션이 도는 동안에는 그 잠금이
    // `LOCK_TIME` 의 주인이다(세션 시작이 이미 활성 잠금을 거부하므로 겹칠 수 없다). 그래서 휴식
    // 차단을 끈 사용자에게는 세션뿐 아니라 **그 잠금까지** 함께 물러나야 한다. 세션만 물러나면
    // 아래 `isLockTime` 이 그대로 남아 휴식에도 계속 막힌다.
    //
    // 루틴·목표 잠금·부모 모드·즉시 차단은 세션이 만든 것이 아니므로 이 후퇴의 영향을 받지 않는다.
    val isPomodoroSessionActive = PomodoroPolicy.isActive(session = pomodoroSession, now = nowInstant)
    val releasesOwnLockForBreak = isPomodoroSessionActive && !isShouldPomodoroBlock
    // Emergency unlock opens the self-control locks and stops at this one. Those were set by the
    // person now asking to escape them, so the escape hatch is theirs to use; parent mode was not,
    // and a button that opens everything three times a day would leave the guardian PIN guarding
    // nothing. The genuine emergency — placing a call — is already exempt above, ahead of parent
    // mode, so it never depended on this.
    if (isEmergencyUnlocked && !isShouldParentModeBlock) return null
    val isEffectiveLockTime = isLockTime && !releasesOwnLockForBreak
    val isBlocking = prefs.isKeep ||
        isEffectiveLockTime ||
        isShouldRoutineBlock ||
        isShouldGoalLockBlock ||
        isShouldParentModeBlock ||
        isShouldPomodoroBlock
    if (!isBlocking) return null
    if (
        !prefs.selectedAppPackages.contains(packageName) &&
        !isShouldRoutineBlock &&
        !isShouldGoalLockBlock &&
        !isShouldParentModeBlock
    ) return null

    // 루틴·목표 잠금·부모 모드는 세션보다 먼저 있던 약속이므로 출처를 그대로 지킨다. 집중 세션이
    // 도는 동안 루틴 차단이 `pomodoro` 로 기록되면, 세션이 끝났을 때 그 차단도 함께 끝난 것처럼
    // 읽힌다. 세션은 그 위에 얹히는 것이지 기존 차단을 가져가는 것이 아니다.
    val blockSource = when {
        isShouldRoutineBlock -> AnalyticsBlockSource.ROUTINE
        isShouldGoalLockBlock -> AnalyticsBlockSource.GOAL_LOCK
        isShouldParentModeBlock -> AnalyticsBlockSource.PARENT_MODE
        isShouldPomodoroBlock -> AnalyticsBlockSource.POMODORO
        isEffectiveLockTime -> AnalyticsBlockSource.TIMED_LOCK
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

package com.uiery.keep.lockscreen

import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.analytics.AnalyticsSource

enum class LockScreenEntryPoint {
    BlockActivity,
    LockRoute,
}

enum class LockScreenMode {
    ManualKeep,
    TimedLock,
    Routine,
    GoalLock,
    ParentMode,

    /**
     * 집중 세션이 만든 차단.
     *
     * 잠금 자체는 타이머 잠금과 같지만 화면이 하는 말이 다르다 — 남은 시간뿐 아니라 지금이
     * 집중인지 휴식인지, 몇 번째 사이클인지를 말해야 한다. 이 값을 빼면 [ManualKeep] 으로
     * 떨어져 세션 문구가 통째로 사라진다.
     */
    Pomodoro,
}

data class LockScreenEntry(
    val entryPoint: LockScreenEntryPoint,
    val mode: LockScreenMode,
    val blockSource: String,
    val emergencyUnlockAnalyticsSource: String,
    val blockedPackageName: String = "",
    val lockTime: String? = null,
    val routineId: String? = null,
    val goalLockId: String? = null,
) {
    val isRoutine: Boolean
        get() = mode == LockScreenMode.Routine

    companion object {
        fun fromBlockActivity(
            packageName: String,
            blockSource: String,
            routineId: String?,
            goalLockId: String?,
        ): LockScreenEntry {
            val mode = blockSource.toLockScreenMode()
            return LockScreenEntry(
                entryPoint = LockScreenEntryPoint.BlockActivity,
                mode = mode,
                blockSource = mode.toAnalyticsBlockSource(),
                emergencyUnlockAnalyticsSource = AnalyticsSource.BLOCK_SCREEN,
                blockedPackageName = packageName,
                routineId = routineId.takeIf { mode == LockScreenMode.Routine },
                goalLockId = goalLockId.takeIf { mode == LockScreenMode.GoalLock },
            )
        }

        fun fromLockRoute(
            lockTime: String?,
            isRoutine: Boolean,
        ): LockScreenEntry =
            LockScreenEntry(
                entryPoint = LockScreenEntryPoint.LockRoute,
                mode = if (isRoutine) LockScreenMode.Routine else LockScreenMode.TimedLock,
                blockSource = if (isRoutine) AnalyticsBlockSource.ROUTINE else AnalyticsBlockSource.TIMED_LOCK,
                emergencyUnlockAnalyticsSource = AnalyticsSource.LOCK_SCREEN,
                lockTime = lockTime,
            )
    }
}

fun String?.toLockScreenMode(): LockScreenMode =
    when (this) {
        AnalyticsBlockSource.TIMED_LOCK -> LockScreenMode.TimedLock
        AnalyticsBlockSource.ROUTINE -> LockScreenMode.Routine
        AnalyticsBlockSource.GOAL_LOCK -> LockScreenMode.GoalLock
        AnalyticsBlockSource.PARENT_MODE -> LockScreenMode.ParentMode
        AnalyticsBlockSource.POMODORO -> LockScreenMode.Pomodoro
        else -> LockScreenMode.ManualKeep
    }

fun LockScreenMode.toAnalyticsBlockSource(): String =
    when (this) {
        LockScreenMode.ManualKeep -> AnalyticsBlockSource.MANUAL_KEEP
        LockScreenMode.TimedLock -> AnalyticsBlockSource.TIMED_LOCK
        LockScreenMode.Routine -> AnalyticsBlockSource.ROUTINE
        LockScreenMode.GoalLock -> AnalyticsBlockSource.GOAL_LOCK
        LockScreenMode.ParentMode -> AnalyticsBlockSource.PARENT_MODE
        LockScreenMode.Pomodoro -> AnalyticsBlockSource.POMODORO
    }

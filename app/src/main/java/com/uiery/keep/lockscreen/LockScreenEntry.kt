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
        else -> LockScreenMode.ManualKeep
    }

fun LockScreenMode.toAnalyticsBlockSource(): String =
    when (this) {
        LockScreenMode.ManualKeep -> AnalyticsBlockSource.MANUAL_KEEP
        LockScreenMode.TimedLock -> AnalyticsBlockSource.TIMED_LOCK
        LockScreenMode.Routine -> AnalyticsBlockSource.ROUTINE
        LockScreenMode.GoalLock -> AnalyticsBlockSource.GOAL_LOCK
        LockScreenMode.ParentMode -> AnalyticsBlockSource.PARENT_MODE
    }

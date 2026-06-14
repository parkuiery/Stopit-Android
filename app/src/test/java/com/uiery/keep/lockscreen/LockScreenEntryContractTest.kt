package com.uiery.keep.lockscreen

import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.analytics.AnalyticsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockScreenEntryContractTest {
    @Test
    fun blockActivityEntryNormalizesManualKeepAsSharedBlockSurface() {
        val entry = LockScreenEntry.fromBlockActivity(
            packageName = "com.example.blocked",
            blockSource = "unexpected",
            routineId = null,
            goalLockId = null,
        )

        assertEquals(LockScreenEntryPoint.BlockActivity, entry.entryPoint)
        assertEquals(LockScreenMode.ManualKeep, entry.mode)
        assertEquals(AnalyticsBlockSource.MANUAL_KEEP, entry.blockSource)
        assertEquals(AnalyticsSource.BLOCK_SCREEN, entry.emergencyUnlockAnalyticsSource)
        assertEquals("com.example.blocked", entry.blockedPackageName)
        assertNull(entry.routineId)
        assertNull(entry.goalLockId)
    }

    @Test
    fun blockActivityEntryCarriesRoutineAndGoalIdentifiersThroughSharedBoundary() {
        val routineEntry = LockScreenEntry.fromBlockActivity(
            packageName = "com.example.routine",
            blockSource = AnalyticsBlockSource.ROUTINE,
            routineId = "routine-42",
            goalLockId = "ignored-goal",
        )
        val goalEntry = LockScreenEntry.fromBlockActivity(
            packageName = "com.example.goal",
            blockSource = AnalyticsBlockSource.GOAL_LOCK,
            routineId = "ignored-routine",
            goalLockId = "goal-77",
        )

        assertEquals(LockScreenMode.Routine, routineEntry.mode)
        assertEquals(AnalyticsBlockSource.ROUTINE, routineEntry.blockSource)
        assertEquals("routine-42", routineEntry.routineId)
        assertNull(routineEntry.goalLockId)

        assertEquals(LockScreenMode.GoalLock, goalEntry.mode)
        assertEquals(AnalyticsBlockSource.GOAL_LOCK, goalEntry.blockSource)
        assertNull(goalEntry.routineId)
        assertEquals("goal-77", goalEntry.goalLockId)
    }

    @Test
    fun lockRouteEntryMapsTimerAndRoutineToSameSharedBoundary() {
        val timerEntry = LockScreenEntry.fromLockRoute(
            lockTime = "2099-01-01T00:00:00",
            isRoutine = false,
        )
        val routineEntry = LockScreenEntry.fromLockRoute(
            lockTime = null,
            isRoutine = true,
        )

        assertEquals(LockScreenEntryPoint.LockRoute, timerEntry.entryPoint)
        assertEquals(LockScreenMode.TimedLock, timerEntry.mode)
        assertEquals(AnalyticsBlockSource.TIMED_LOCK, timerEntry.blockSource)
        assertEquals(AnalyticsSource.LOCK_SCREEN, timerEntry.emergencyUnlockAnalyticsSource)
        assertEquals("2099-01-01T00:00:00", timerEntry.lockTime)

        assertEquals(LockScreenEntryPoint.LockRoute, routineEntry.entryPoint)
        assertEquals(LockScreenMode.Routine, routineEntry.mode)
        assertEquals(AnalyticsBlockSource.ROUTINE, routineEntry.blockSource)
        assertEquals(AnalyticsSource.LOCK_SCREEN, routineEntry.emergencyUnlockAnalyticsSource)
        assertNull(routineEntry.lockTime)
    }
}

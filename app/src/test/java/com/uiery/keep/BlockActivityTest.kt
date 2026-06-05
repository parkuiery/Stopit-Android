package com.uiery.keep

import com.uiery.keep.analytics.AnalyticsBlockSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockActivityTest {
    @Test
    fun routineIdExtraAcceptsStringAndLegacyLongValues() {
        assertEquals("42", normalizeRoutineIdExtra("42"))
        assertEquals("42", normalizeRoutineIdExtra(42L))
    }

    @Test
    fun routineIdExtraRejectsBlankOrUnsupportedValues() {
        assertNull(normalizeRoutineIdExtra(null))
        assertNull(normalizeRoutineIdExtra(""))
        assertNull(normalizeRoutineIdExtra("   "))
        assertNull(normalizeRoutineIdExtra(true))
    }

    @Test
    fun blockActivityArgsNormalizeBlockSourceAndRoutineId() {
        val args = createBlockActivityArgs(
            packageName = "com.example.blocked",
            blockSource = AnalyticsBlockSource.ROUTINE,
            rawRoutineId = 42L,
            rawGoalLockId = null,
        )

        assertEquals("com.example.blocked", args.packageName)
        assertEquals(AnalyticsBlockSource.ROUTINE, args.blockSource)
        assertEquals("42", args.routineId)
        assertNull(args.goalLockId)
    }

    @Test
    fun blockActivityArgsKeepGoalLockSourceAndGoalLockId() {
        val args = createBlockActivityArgs(
            packageName = "com.example.goal",
            blockSource = AnalyticsBlockSource.GOAL_LOCK,
            rawRoutineId = null,
            rawGoalLockId = 77L,
        )

        assertEquals("com.example.goal", args.packageName)
        assertEquals(AnalyticsBlockSource.GOAL_LOCK, args.blockSource)
        assertNull(args.routineId)
        assertEquals("77", args.goalLockId)
    }

    @Test
    fun blockActivityArgsKeepManualAndTimedLockRoutineIdNull() {
        val manualArgs = createBlockActivityArgs(
            packageName = "com.example.manual",
            blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            rawRoutineId = null,
            rawGoalLockId = null,
        )
        val timedArgs = createBlockActivityArgs(
            packageName = "com.example.timed",
            blockSource = AnalyticsBlockSource.TIMED_LOCK,
            rawRoutineId = null,
            rawGoalLockId = null,
        )

        assertEquals(AnalyticsBlockSource.MANUAL_KEEP, manualArgs.blockSource)
        assertNull(manualArgs.routineId)
        assertNull(manualArgs.goalLockId)
        assertEquals(AnalyticsBlockSource.TIMED_LOCK, timedArgs.blockSource)
        assertNull(timedArgs.routineId)
        assertNull(timedArgs.goalLockId)
    }
}

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
        )

        assertEquals("com.example.blocked", args.packageName)
        assertEquals(AnalyticsBlockSource.ROUTINE, args.blockSource)
        assertEquals("42", args.routineId)
    }

    @Test
    fun blockActivityArgsKeepManualAndTimedLockRoutineIdNull() {
        val manualArgs = createBlockActivityArgs(
            packageName = "com.example.manual",
            blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            rawRoutineId = null,
        )
        val timedArgs = createBlockActivityArgs(
            packageName = "com.example.timed",
            blockSource = AnalyticsBlockSource.TIMED_LOCK,
            rawRoutineId = null,
        )

        assertEquals(AnalyticsBlockSource.MANUAL_KEEP, manualArgs.blockSource)
        assertNull(manualArgs.routineId)
        assertEquals(AnalyticsBlockSource.TIMED_LOCK, timedArgs.blockSource)
        assertNull(timedArgs.routineId)
    }
}

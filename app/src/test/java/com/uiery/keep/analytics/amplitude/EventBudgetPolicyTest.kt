package com.uiery.keep.analytics.amplitude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventBudgetPolicyTest {
    @Test
    fun firstEventInFreshPeriodStartsAtOne() {
        assertEquals(
            1,
            EventBudgetPolicy.nextCount(storedPeriod = null, storedCount = 0, currentPeriod = "2026-6", cap = 3),
        )
    }

    @Test
    fun incrementsWithinSamePeriodUpToCap() {
        assertEquals(3, EventBudgetPolicy.nextCount("2026-6", 2, "2026-6", 3))
    }

    @Test
    fun returnsNullWhenCapAlreadyReached() {
        assertNull(EventBudgetPolicy.nextCount("2026-6", 3, "2026-6", 3))
    }

    @Test
    fun resetsCountOnNewPeriod() {
        assertEquals(1, EventBudgetPolicy.nextCount("2026-6", 3, "2026-7", 3))
    }
}

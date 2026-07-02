package com.uiery.keep.service

import com.uiery.keep.model.LockHistoryModel
import org.junit.Assert.assertEquals
import org.junit.Test

class LockHistoryLedgerTest {
    @Test
    fun summarizeSessionsBuildsSharedSummaryForHistoryAndDetail() {
        val summary =
            summarizeLockHistoryLedger(
                sessions = listOf(
                    lockHistoryModel(
                        id = 1,
                        startTimestamp = 1_746_335_400_000,
                        endTimestamp = 1_746_338_400_000,
                        lockedApps = listOf("com.instagram", "com.youtube"),
                    ),
                    lockHistoryModel(
                        id = 2,
                        startTimestamp = 1_746_339_000_000,
                        endTimestamp = 1_746_343_500_000,
                        lockedApps = listOf("com.youtube", "com.discord"),
                    ),
                    lockHistoryModel(
                        id = 3,
                        startTimestamp = 1_746_421_800_000,
                        endTimestamp = 1_746_423_000_000,
                        lockedApps = listOf("com.discord"),
                    ),
                ),
            )

        assertEquals(8_700_000L, summary.totalDurationMillis)
        assertEquals(4_500_000L, summary.longestDurationMillis)
        assertEquals(3, summary.sessionCount)
        assertEquals(listOf("com.youtube", "com.discord", "com.instagram"), summary.topApps)
        assertEquals(setOf(2_0250504, 2_0250505), summary.groupedSessions.keys.map { it.year * 10_000 + it.monthValue * 100 + it.dayOfMonth }.toSet())
        assertEquals(7_500_000L, summary.durationByDate.values.max())
    }

    private fun lockHistoryModel(
        id: Long,
        startTimestamp: Long,
        endTimestamp: Long,
        lockedApps: List<String>,
    ) = LockHistoryModel(
        id = id,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        durationMillis = endTimestamp - startTimestamp,
        lockedApps = lockedApps,
        isRoutine = false,
    )
}

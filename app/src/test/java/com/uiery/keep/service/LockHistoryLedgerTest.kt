package com.uiery.keep.service

import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.model.LockHistoryModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
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

    @Test
    fun recordSessionPersistsLedgerEntryAndMaintainsLegacySummaryCache() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.LONG_BLOCK_TIME] = 4_000L
                this[PreferencesKey.TOTAL_BLOCK_TIME] = 9_000L
            }
        val dao = RecordingLockHistoryDao()

        recordLockHistorySession(
            dataStore = dataStore,
            lockHistoryDao = dao,
            startTimestamp = 1_000L,
            endTimestamp = 8_000L,
            lockedApps = setOf("com.instagram", "com.youtube"),
            isRoutine = true,
        )

        val snapshot = dataStore.snapshot()
        assertEquals(7_000L, snapshot[PreferencesKey.LONG_BLOCK_TIME])
        assertEquals(16_000L, snapshot[PreferencesKey.TOTAL_BLOCK_TIME])
        assertEquals(
            listOf(
                LockHistoryEntity(
                    startTimestamp = 1_000L,
                    endTimestamp = 8_000L,
                    durationMillis = 7_000L,
                    lockedApps = listOf("com.instagram", "com.youtube"),
                    isRoutine = true,
                ),
            ),
            dao.inserted,
        )
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

private class RecordingLockHistoryDao : LockHistoryDao {
    val inserted = mutableListOf<LockHistoryEntity>()

    override suspend fun insert(entity: LockHistoryEntity) {
        inserted += entity
    }

    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> = emptyFlow()

    override fun fetchAll(): Flow<List<LockHistoryEntity>> = emptyFlow()

    override suspend fun countSuccessfulSessions(): Int = inserted.size

    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int =
        inserted.count { it.startTimestamp >= timestampMillis }
}

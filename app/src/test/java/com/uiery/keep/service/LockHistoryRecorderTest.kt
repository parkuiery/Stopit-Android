package com.uiery.keep.service

import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.database.repository.LockHistorySessionWriter
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LockHistoryRecorderTest {
    @Test
    fun recordSessionPersistsRoomLedgerAndMaintainsLegacyCacheBehindRecorderBoundary() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.LONG_BLOCK_TIME] = 4_000L
                this[PreferencesKey.TOTAL_BLOCK_TIME] = 9_000L
            }
        val dao = RecordingLockHistoryDao()
        val recorder = LockHistoryRecorder(
            dataStore = dataStore,
            lockHistorySessionWriter = LockHistorySessionWriter(dao),
        )

        recorder.recordSession(
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

    @Test
    fun recordSessionClampsNegativeDurationBeforeUpdatingLegacyCacheAndLedger() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.LONG_BLOCK_TIME] = 4_000L
                this[PreferencesKey.TOTAL_BLOCK_TIME] = 9_000L
            }
        val dao = RecordingLockHistoryDao()
        val recorder = LockHistoryRecorder(
            dataStore = dataStore,
            lockHistorySessionWriter = LockHistorySessionWriter(dao),
        )

        recorder.recordSession(
            startTimestamp = 8_000L,
            endTimestamp = 1_000L,
            lockedApps = setOf("com.instagram"),
            isRoutine = false,
        )

        val snapshot = dataStore.snapshot()
        assertEquals(4_000L, snapshot[PreferencesKey.LONG_BLOCK_TIME])
        assertEquals(9_000L, snapshot[PreferencesKey.TOTAL_BLOCK_TIME])
        assertEquals(0L, dao.inserted.single().durationMillis)
    }
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

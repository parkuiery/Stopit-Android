package com.uiery.keep.feature.lockhistory

import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LockHistoryRepositoryTest {
    @Test
    fun sessionsInRangeMapsDaoEntitiesToDomainModels() = runBlocking {
        val repository = LockHistoryRepository(
            lockHistoryDao = FakeLockHistoryDao(
                sessions = listOf(
                    lockHistoryEntity(
                        id = 7L,
                        startTimestamp = 1_000L,
                        endTimestamp = 4_000L,
                        lockedApps = listOf("com.example.reader"),
                        isRoutine = true,
                    ),
                ),
            ),
        )

        val sessions = repository.sessionsInRange(0L, 5_000L).first()

        assertEquals(1, sessions.size)
        assertEquals(7L, sessions.single().id)
        assertEquals(3_000L, sessions.single().durationMillis)
        assertEquals(listOf("com.example.reader"), sessions.single().lockedApps)
        assertEquals(true, sessions.single().isRoutine)
    }

    @Test
    fun blockedAppsByFrequencyCountsAllHistorySessionsWithoutExposingDao() = runBlocking {
        val repository = LockHistoryRepository(
            lockHistoryDao = FakeLockHistoryDao(
                sessions = listOf(
                    lockHistoryEntity(lockedApps = listOf("com.example.chat", "com.example.video")),
                    lockHistoryEntity(lockedApps = listOf("com.example.chat")),
                ),
            ),
        )

        val blockedApps = repository.blockedAppsByFrequency().first()

        assertEquals(
            listOf(
                "com.example.chat" to 2,
                "com.example.video" to 1,
            ),
            blockedApps,
        )
    }
}

private class FakeLockHistoryDao(
    private val sessions: List<LockHistoryEntity>,
) : LockHistoryDao {
    override suspend fun insert(entity: LockHistoryEntity) = Unit

    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> =
        flowOf(sessions.filter { it.startTimestamp >= startMillis && it.startTimestamp < endMillis })

    override fun fetchAll(): Flow<List<LockHistoryEntity>> = flowOf(sessions)

    override suspend fun countSuccessfulSessions(): Int = sessions.size

    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int =
        sessions.count { it.startTimestamp >= timestampMillis }
}

private fun lockHistoryEntity(
    id: Long = 0L,
    startTimestamp: Long = 1_000L,
    endTimestamp: Long = 2_000L,
    lockedApps: List<String> = emptyList(),
    isRoutine: Boolean = false,
): LockHistoryEntity = LockHistoryEntity(
    id = id,
    startTimestamp = startTimestamp,
    endTimestamp = endTimestamp,
    durationMillis = endTimestamp - startTimestamp,
    lockedApps = lockedApps,
    isRoutine = isRoutine,
)

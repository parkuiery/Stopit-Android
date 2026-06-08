package com.uiery.keep.database.repository

import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import javax.inject.Inject

/**
 * Runtime write boundary for completed lock sessions.
 *
 * Feature lock-history repositories own read-model queries; service/runtime code should
 * use this writer when it needs to append completed sessions to the Room ledger.
 */
class LockHistorySessionWriter @Inject constructor(
    private val lockHistoryDao: LockHistoryDao,
) {
    suspend fun recordSession(
        startTimestamp: Long,
        endTimestamp: Long,
        durationMillis: Long,
        lockedApps: Collection<String>,
        isRoutine: Boolean,
    ) {
        lockHistoryDao.insert(
            LockHistoryEntity(
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp,
                durationMillis = durationMillis,
                lockedApps = lockedApps.toList(),
                isRoutine = isRoutine,
            ),
        )
    }
}

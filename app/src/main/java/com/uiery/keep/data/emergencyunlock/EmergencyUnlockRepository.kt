package com.uiery.keep.data.emergencyunlock

import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import javax.inject.Inject

/**
 * Repository boundary for emergency-unlock Room access.
 *
 * Service orchestration owns request ordering and policy decisions; this repository owns the
 * persistence schema conversion used to count and record unlock history.
 */
class EmergencyUnlockRepository
    @Inject
    constructor(
        private val emergencyUnlockDao: EmergencyUnlockDao,
    ) {
        suspend fun recordUnlock(
            timestamp: Long,
            reason: String,
            customReason: String?,
            apps: Set<String>,
            durationMinutes: Int,
        ): Long =
            emergencyUnlockDao.insert(
                EmergencyUnlockEntity(
                    timestamp = timestamp,
                    reason = reason,
                    customReason = customReason,
                    unlockedApps = apps.toList(),
                    durationMinutes = durationMinutes,
                ),
            )

        suspend fun deleteById(id: Long) {
            emergencyUnlockDao.deleteById(id)
        }

        suspend fun countToday(todayStart: Long): Int = emergencyUnlockDao.countToday(todayStart)

        suspend fun countSince(timestampMillis: Long): Int = emergencyUnlockDao.countSince(timestampMillis)
    }

package com.uiery.keep.service

import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import javax.inject.Inject

/**
 * Repository boundary for emergency-unlock Room access.
 *
 * [EmergencyUnlockCoordinator] owns request orchestration and policy ordering; this repository owns
 * the persistence calls used to count and record unlock history.
 */
class EmergencyUnlockRepository
    @Inject
    constructor(
        private val emergencyUnlockDao: EmergencyUnlockDao,
    ) {
        suspend fun insert(entity: EmergencyUnlockEntity): Long = emergencyUnlockDao.insert(entity)

        suspend fun deleteById(id: Long) {
            emergencyUnlockDao.deleteById(id)
        }

        suspend fun countToday(todayStart: Long): Int = emergencyUnlockDao.countToday(todayStart)

        suspend fun countSince(timestampMillis: Long): Int = emergencyUnlockDao.countSince(timestampMillis)
    }

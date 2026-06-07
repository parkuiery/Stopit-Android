package com.uiery.keep.feature.review

import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.dao.LockHistoryDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository boundary for persisted signals used by review-prompt eligibility.
 *
 * The evaluator owns review policy ordering; this class owns the Room DAO reads so feature/runtime
 * callers do not need to know which tables back recent emergency-unlock and success evidence.
 */
@Singleton
class ReviewEligibilityRepository
    @Inject
    constructor(
        private val emergencyUnlockDao: EmergencyUnlockDao,
        private val lockHistoryDao: LockHistoryDao,
    ) {
        suspend fun countRecentEmergencyUnlocks(sinceMillis: Long): Int =
            emergencyUnlockDao.countSince(sinceMillis)

        suspend fun countRecentSuccessfulSessions(sinceMillis: Long): Int =
            lockHistoryDao.countSuccessfulSessionsSince(sinceMillis)
    }

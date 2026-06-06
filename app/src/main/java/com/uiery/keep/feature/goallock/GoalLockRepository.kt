package com.uiery.keep.feature.goallock

import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalLockRepository
    @Inject
    constructor(
        private val goalLockDao: GoalLockDao,
    ) {
        internal fun create(goalLock: GoalLock): Long =
            goalLockDao.insert(GoalLockEntity.fromDomain(goalLock))

        internal fun fetch(id: Long): GoalLock? =
            goalLockDao.fetch(id)?.toDomain()

        internal fun fetchAll(): Flow<List<GoalLock>> =
            goalLockDao.fetchAll().map { goalLocks -> goalLocks.map { it.toDomain() } }

        internal fun update(goalLock: GoalLock) {
            goalLockDao.update(GoalLockEntity.fromDomain(goalLock))
        }
    }

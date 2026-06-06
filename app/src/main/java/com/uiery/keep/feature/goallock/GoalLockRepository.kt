package com.uiery.keep.feature.goallock

import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import javax.inject.Inject

internal class GoalLockRepository
    @Inject
    constructor(
        private val goalLockDao: GoalLockDao,
    ) {
        fun create(goalLock: GoalLock): Long =
            goalLockDao.insert(GoalLockEntity.fromDomain(goalLock))

        fun fetch(id: Long): GoalLock? =
            goalLockDao.fetch(id)?.toDomain()

        fun update(goalLock: GoalLock) {
            goalLockDao.update(GoalLockEntity.fromDomain(goalLock))
        }
    }

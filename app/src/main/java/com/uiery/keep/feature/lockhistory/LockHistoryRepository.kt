package com.uiery.keep.feature.lockhistory

import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.model.LockHistoryModel
import com.uiery.keep.database.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LockHistoryRepository @Inject constructor(
    private val lockHistoryDao: LockHistoryDao,
) {
    fun sessionsInRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryModel>> =
        lockHistoryDao.fetchByDateRange(startMillis, endMillis).map { sessions ->
            sessions.map { it.toModel() }
        }

    fun blockedAppsByFrequency(): Flow<List<Pair<String, Int>>> =
        lockHistoryDao.fetchAll().map { sessions ->
            sessions
                .flatMap { it.toModel().lockedApps }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key to it.value }
        }
}

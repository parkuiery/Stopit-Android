package com.uiery.keep.database.mapper

import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.model.LockHistoryModel

fun LockHistoryEntity.toModel(): LockHistoryModel = LockHistoryModel(
    id = id,
    startTimestamp = startTimestamp,
    endTimestamp = endTimestamp,
    durationMillis = durationMillis,
    lockedApps = lockedApps,
    isRoutine = isRoutine,
)

fun LockHistoryModel.toEntity(): LockHistoryEntity = LockHistoryEntity(
    id = id,
    startTimestamp = startTimestamp,
    endTimestamp = endTimestamp,
    durationMillis = durationMillis,
    lockedApps = lockedApps,
    isRoutine = isRoutine,
)

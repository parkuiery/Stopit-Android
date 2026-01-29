package com.uiery.keep.model

import com.uiery.keep.database.entity.LockHistoryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class LockHistoryModel(
    val id: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMillis: Long,
    val lockedApps: List<String>,
    val isRoutine: Boolean,
) {
    val startDateTime: LocalDateTime
        get() = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(startTimestamp),
            ZoneId.systemDefault()
        )

    val endDateTime: LocalDateTime
        get() = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(endTimestamp),
            ZoneId.systemDefault()
        )

    val date: LocalDate
        get() = startDateTime.toLocalDate()
}

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

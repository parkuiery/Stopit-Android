package com.uiery.keep.model

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

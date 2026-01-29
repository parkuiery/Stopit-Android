package com.uiery.keep.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lock_history")
data class LockHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_timestamp") val startTimestamp: Long,
    @ColumnInfo(name = "end_timestamp") val endTimestamp: Long,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    @ColumnInfo(name = "locked_apps") val lockedApps: List<String>,
    @ColumnInfo(name = "is_routine") val isRoutine: Boolean,
)

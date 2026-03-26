package com.uiery.keep.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalTime
import java.time.DayOfWeek

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "start_time") val startTime: LocalTime,
    @ColumnInfo(name = "end_time") val endTime: LocalTime,
    @ColumnInfo(name = "repeatDays") val repeatDays: List<DayOfWeek>,
    @ColumnInfo(name = "lockApplications") val lockApplications: List<String>,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "change_lock_hours") val changeLockHours: Int? = null,
)
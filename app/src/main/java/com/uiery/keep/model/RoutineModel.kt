package com.uiery.keep.model

import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.util.toDayOfWeekList
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class RoutineModel(
    val id: Long,
    val name: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val repeatDays: String,
    val lockApplications: List<String>?,
    val isEnabled: Boolean,
    val changeLockHours: Int? = null,
)

fun RoutineEntity.toModel() = RoutineModel(
    id = id,
    name = name,
    startTime = startTime,
    endTime = endTime,
    repeatDays = repeatDays.toRepeatDaysBinary(),
    lockApplications = lockApplications,
    isEnabled = isEnabled,
    changeLockHours = changeLockHours,
)

fun RoutineModel.toEntity() = RoutineEntity(
    id = id,
    name = name,
    startTime = startTime,
    endTime = endTime,
    repeatDays = repeatDays.toDayOfWeekList(),
    lockApplications = lockApplications ?: emptyList(),
    isEnabled = isEnabled,
    changeLockHours = changeLockHours,
)
package com.uiery.keep.database.mapper

import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.toDayOfWeekList
import com.uiery.keep.util.toRepeatDaysBinary

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

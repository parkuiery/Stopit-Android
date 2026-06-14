package com.uiery.keep.model

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

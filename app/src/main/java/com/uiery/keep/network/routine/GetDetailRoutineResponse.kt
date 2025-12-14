package com.uiery.keep.network.routine

data class GetDetailRoutineResponse(
    val id: String,
    val localRoutineId: String?,
    val name: String,
    val startTime: String,
    val endTime: String,
    val repeatDays: String,
    val lockApplications: List<String>?,
    val isEnabled: Boolean,
)

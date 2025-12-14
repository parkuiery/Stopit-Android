package com.uiery.keep.network.routine

data class UpdateRoutineResponse (
    val id: String,
    val localRoutineId: String?,
    val name: String,
    val startTime: String,
    val endTime: String,
    val repeatDays: String,
    val lockApplications: List<String>?,
    val isEnabled: Boolean,
)
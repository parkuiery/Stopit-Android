package com.uiery.keep.network.routine

data class CreateRoutineRequest(
    val localRoutineId: String?,
    val name: String,
    val startTime: String,
    val endTime: String,
    val repeatDays: String,
    val lockApplications: List<String>?,
    val deviceId: String,
)
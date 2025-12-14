package com.uiery.keep.network.routine

data class UpdateRoutineRequest(
    val name: String,
    val startTime: String,
    val endTime: String,
    val repeatDays: String,
    val lockApplications: List<String>,
)

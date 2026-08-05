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
    // 기존에 저장된 루틴 JSON 에는 이 필드가 없다. 기본값이 있어야 캐시를 다시 읽을 수 있다.
    val lockWebsites: List<String>? = null,
    val isEnabled: Boolean,
    val changeLockHours: Int? = null,
)

package com.uiery.keep.receiver

import com.uiery.keep.model.RoutineModel
import kotlinx.serialization.json.Json

object RoutineReceiverPolicy {
    fun decodeStoredRoutines(routinesJson: String?): List<RoutineModel> {
        if (routinesJson.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            Json.decodeFromString<List<RoutineModel>>(routinesJson)
        }.getOrDefault(emptyList())
    }

    fun findEnabledRoutineToReschedule(
        routines: List<RoutineModel>,
        routineId: Long,
    ): RoutineModel? = routines.firstOrNull { it.id == routineId && it.isEnabled }
}

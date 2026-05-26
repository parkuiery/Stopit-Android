package com.uiery.keep.receiver

import android.content.Intent
import com.uiery.keep.model.RoutineModel
import kotlinx.serialization.json.Json

data class RoutineAlarmTrigger(
    val routineName: String,
    val routineId: Long,
)

object RoutineReceiverPolicy {
    // Room is the authoritative routine source of truth.
    // PreferencesKey.ROUTINES is only a runtime compatibility cache that may be rehydrated
    // from Room after boot/restore/alarm entry, never the primary read path.
    fun shouldRestoreRoutinesOnBoot(action: String?): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED

    fun parseRoutineAlarmTrigger(
        action: String?,
        routineName: String?,
        routineId: Long,
    ): RoutineAlarmTrigger? {
        if (action != RoutineAlarmReceiver.ACTION_ROUTINE_ALARM) {
            return null
        }

        val validatedRoutineName = routineName ?: return null
        if (routineId == -1L) {
            return null
        }

        return RoutineAlarmTrigger(
            routineName = validatedRoutineName,
            routineId = routineId,
        )
    }

    fun decodeStoredRoutines(routinesJson: String?): List<RoutineModel> {
        if (routinesJson.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            Json.decodeFromString<List<RoutineModel>>(routinesJson)
        }.getOrDefault(emptyList())
    }

    fun resolveRoutines(
        storedRoutines: List<RoutineModel>,
        databaseRoutines: List<RoutineModel>,
    ): List<RoutineModel> = databaseRoutines

    fun shouldRehydrateStoredRoutines(
        storedRoutines: List<RoutineModel>,
        databaseRoutines: List<RoutineModel>,
    ): Boolean = storedRoutines != databaseRoutines

    fun findEnabledRoutineToReschedule(
        routines: List<RoutineModel>,
        routineId: Long,
    ): RoutineModel? = routines.firstOrNull { it.id == routineId && it.isEnabled }
}

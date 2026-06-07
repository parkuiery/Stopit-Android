package com.uiery.keep.receiver

import android.content.Intent
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineStartNotificationResult
import kotlinx.serialization.json.Json

data class RoutineAlarmTrigger(
    val routineName: String,
    val routineId: Long,
)

data class PendingRoutineStartNotice(
    val message: String,
)

data class PendingRoutineStartNoticeDrain(
    val message: String?,
    val remainingStoredValue: String?,
)

data class RoutineScheduleApplication(
    val routines: List<RoutineModel>,
    val disabledRoutineIds: Set<Long>,
    val shouldResetAlarmPermissionPrompt: Boolean,
)

object RoutineReceiverPolicy {
    // Room is the authoritative routine source of truth.
    // PreferencesKey.ROUTINES is only a runtime compatibility cache that may be rehydrated
    // from Room after boot/restore/alarm entry, never the primary read path.
    fun shouldRestoreRoutinesOnBoot(action: String?): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED

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

    fun shouldRewriteCompatibilityCache(
        storedRoutines: List<RoutineModel>,
        databaseRoutines: List<RoutineModel>,
        updatedRoutines: List<RoutineModel>,
    ): Boolean = storedRoutines != updatedRoutines || databaseRoutines != updatedRoutines

    fun findEnabledRoutineToReschedule(
        routines: List<RoutineModel>,
        routineId: Long,
    ): RoutineModel? = routines.firstOrNull { it.id == routineId && it.isEnabled }

    fun applyScheduleResult(
        routines: List<RoutineModel>,
        routineId: Long,
        scheduleResult: RoutineScheduleResult,
    ): RoutineScheduleApplication {
        if (scheduleResult != RoutineScheduleResult.MissingExactAlarmPermission) {
            return RoutineScheduleApplication(
                routines = routines,
                disabledRoutineIds = emptySet(),
                shouldResetAlarmPermissionPrompt = false,
            )
        }

        val disabledRoutineIds = routines
            .filter { it.id == routineId && it.isEnabled }
            .map { it.id }
            .toSet()

        if (disabledRoutineIds.isEmpty()) {
            return RoutineScheduleApplication(
                routines = routines,
                disabledRoutineIds = emptySet(),
                shouldResetAlarmPermissionPrompt = false,
            )
        }

        return RoutineScheduleApplication(
            routines = routines.map { routine ->
                if (routine.id in disabledRoutineIds) routine.copy(isEnabled = false) else routine
            },
            disabledRoutineIds = disabledRoutineIds,
            shouldResetAlarmPermissionPrompt = true,
        )
    }

    fun applyMissingExactAlarmPermission(
        routines: List<RoutineModel>,
        routineId: Long,
    ): RoutineScheduleApplication = applyScheduleResult(
        routines = routines,
        routineId = routineId,
        scheduleResult = RoutineScheduleResult.MissingExactAlarmPermission,
    )

    fun buildPendingRoutineStartNotice(
        notificationResult: RoutineStartNotificationResult,
        fallbackMessage: String,
    ): PendingRoutineStartNotice? = when {
        notificationResult == RoutineStartNotificationResult.Posted -> null
        fallbackMessage.isBlank() -> null
        else -> PendingRoutineStartNotice(message = fallbackMessage)
    }

    fun enqueuePendingRoutineStartNotice(
        storedValue: String?,
        notice: PendingRoutineStartNotice,
    ): String? = encodePendingRoutineStartNotices(
        decodePendingRoutineStartNotices(storedValue) + notice.message,
    )

    fun drainNextPendingRoutineStartNotice(storedValue: String?): PendingRoutineStartNoticeDrain {
        val notices = decodePendingRoutineStartNotices(storedValue)
        val message = notices.firstOrNull()
        val remaining = notices.drop(1)
        return PendingRoutineStartNoticeDrain(
            message = message,
            remainingStoredValue = encodePendingRoutineStartNotices(remaining),
        )
    }

    fun decodePendingRoutineStartNotices(storedValue: String?): List<String> {
        if (storedValue.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            Json.decodeFromString<List<String>>(storedValue)
        }.getOrElse {
            listOf(storedValue)
        }.filter { it.isNotBlank() }
    }

    fun encodePendingRoutineStartNotices(notices: List<String>): String? {
        val normalized = notices.filter { it.isNotBlank() }
        if (normalized.isEmpty()) {
            return null
        }
        return Json.encodeToString(normalized)
    }
}

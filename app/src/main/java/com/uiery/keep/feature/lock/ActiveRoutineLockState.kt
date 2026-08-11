package com.uiery.keep.feature.lock

import com.uiery.keep.appselection.BlockExemptPackagePolicy
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.currentRoutineWindowStartDateTime
import com.uiery.keep.util.currentRoutineWindowEndDateTime
import com.uiery.keep.util.isRoutineActiveNow
import com.uiery.keep.util.toDayOfWeekList
import java.time.LocalDateTime

internal data class ActiveRoutineLockState(
    val routines: List<RoutineModel>,
    val blockedApps: Set<String>,
    val blockedWebDomains: Set<String>,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
)

internal fun resolveActiveRoutineLockState(
    routines: List<RoutineModel>,
    nowDateTime: LocalDateTime = LocalDateTime.now(),
    exemptPackages: Set<String> = emptySet(),
): ActiveRoutineLockState {
    val activeRoutines =
        routines.filter { routine ->
            routine.isEnabled &&
                isRoutineActiveNow(
                    startTime = routine.startTime,
                    endTime = routine.endTime,
                    repeatDays = routine.repeatDays.toDayOfWeekList(),
                    nowDateTime = nowDateTime,
                )
        }

    if (activeRoutines.isEmpty()) {
        return ActiveRoutineLockState(
            routines = emptyList(),
            blockedApps = emptySet(),
            blockedWebDomains = emptySet(),
            startTime = nowDateTime,
            endTime = nowDateTime,
        )
    }

    val blockedApps =
        BlockExemptPackagePolicy.filterBlockable(
            packages = activeRoutines
                .flatMap { it.lockApplications.orEmpty() }
                .toCollection(linkedSetOf()),
            exemptPackages = exemptPackages,
        )
    // 도메인은 패키지가 아니므로 앱 면제 정책의 대상이 아니다.
    val blockedWebDomains = activeRoutines
        .flatMap { it.lockWebsites.orEmpty() }
        .toCollection(linkedSetOf())
    val startTime =
        activeRoutines.minOf { routine ->
            currentRoutineWindowStartDateTime(
                startTime = routine.startTime,
                endTime = routine.endTime,
                nowDateTime = nowDateTime,
            )
        }
    val endTime =
        activeRoutines.maxOf { routine ->
            currentRoutineWindowEndDateTime(
                startTime = routine.startTime,
                endTime = routine.endTime,
                nowDateTime = nowDateTime,
            )
        }

    return ActiveRoutineLockState(
        routines = activeRoutines,
        blockedApps = blockedApps,
        blockedWebDomains = blockedWebDomains,
        startTime = startTime,
        endTime = endTime,
    )
}

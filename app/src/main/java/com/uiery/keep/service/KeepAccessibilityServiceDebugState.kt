package com.uiery.keep.service

import android.content.Context
import java.io.File

/**
 * Instrumentation-only observability for waiting until KeepAccessibilityService
 * has actually bound and consumed runtime state.
 */
object KeepAccessibilityServiceDebugState {
    data class Snapshot(
        val isServiceConnected: Boolean = false,
        val observedIsKeep: Boolean = false,
        val observedPreventUninstall: Boolean = true,
        val observedSelectedAppPackages: Set<String> = emptySet(),
        val observedEmergencyUnlockApps: Set<String> = emptySet(),
        val observedEmergencyUnlockExpireTimeMillis: Long = 0L,
        val observedParentModeState: String? = null,
        val observedParentModeAllowedAppCount: Int = 0,
        val lastCountdownNotificationExpireTimeMillis: Long = 0L,
        val lastCountdownNotificationPostResult: String? = null,
        val lastWindowStateChangedPackage: String? = null,
        val lastLaunchedBlockPackage: String? = null,
        val lastLaunchedBlockSource: String? = null,
        val lastLaunchedRoutineId: String? = null,
        val lastLaunchedGoalLockId: String? = null,
        val lastDismissedUninstallPackage: String? = null,
        val lastRuntimeFlowErrorSource: String? = null,
        val lastRuntimeFlowErrorType: String? = null,
        val lastRuntimeFlowRetryAttempt: Long = 0L,
        val lastRuntimeFlowRetryDelayMillis: Long = 0L,
    )

    @Volatile
    private var snapshot: Snapshot = Snapshot()

    fun update(
        context: Context,
        transform: (Snapshot) -> Snapshot,
    ) {
        snapshot = transform(snapshot)
        persist(context, snapshot)
    }

    fun reset(context: Context) {
        snapshot = Snapshot()
        persist(context, snapshot)
    }

    fun read(context: Context): Snapshot {
        val file = stateFile(context)
        if (!file.exists()) return Snapshot()
        val parts = file.readText().split('\n')
        if (parts.size < 8) return Snapshot()
        val hasNotificationFields = parts.size >= 11
        val hasLaunchAttributionFields = parts.size >= 14
        val hasParentModeFields = parts.size >= 16
        val hasRuntimeFlowFields = parts.size >= 20
        return Snapshot(
            isServiceConnected = parts[0].toBoolean(),
            observedIsKeep = parts[1].toBoolean(),
            observedPreventUninstall = parts[2].toBoolean(),
            observedSelectedAppPackages = decodeSet(parts[3]),
            observedEmergencyUnlockApps = decodeSet(parts[4]),
            observedEmergencyUnlockExpireTimeMillis = if (hasNotificationFields) parts[5].toLongOrNull() ?: 0L else 0L,
            lastCountdownNotificationExpireTimeMillis = if (hasNotificationFields) parts[6].toLongOrNull() ?: 0L else 0L,
            lastCountdownNotificationPostResult = if (hasNotificationFields) parts[7].ifBlank { null } else null,
            lastWindowStateChangedPackage = parts[if (hasNotificationFields) 8 else 5].ifBlank { null },
            lastLaunchedBlockPackage = parts[if (hasNotificationFields) 9 else 6].ifBlank { null },
            lastLaunchedBlockSource = if (hasLaunchAttributionFields) parts[11].ifBlank { null } else null,
            lastLaunchedRoutineId = if (hasLaunchAttributionFields) parts[12].ifBlank { null } else null,
            lastLaunchedGoalLockId = if (hasLaunchAttributionFields) parts[13].ifBlank { null } else null,
            lastDismissedUninstallPackage = parts[if (hasNotificationFields) 10 else 7].ifBlank { null },
            observedParentModeState = if (hasParentModeFields) parts[14].ifBlank { null } else null,
            observedParentModeAllowedAppCount = if (hasParentModeFields) parts[15].toIntOrNull() ?: 0 else 0,
            lastRuntimeFlowErrorSource = if (hasRuntimeFlowFields) parts[16].ifBlank { null } else null,
            lastRuntimeFlowErrorType = if (hasRuntimeFlowFields) parts[17].ifBlank { null } else null,
            lastRuntimeFlowRetryAttempt = if (hasRuntimeFlowFields) parts[18].toLongOrNull() ?: 0L else 0L,
            lastRuntimeFlowRetryDelayMillis = if (hasRuntimeFlowFields) parts[19].toLongOrNull() ?: 0L else 0L,
        )
    }

    private fun persist(
        context: Context,
        snapshot: Snapshot,
    ) {
        stateFile(context).writeText(
            buildString {
                appendLine(snapshot.isServiceConnected)
                appendLine(snapshot.observedIsKeep)
                appendLine(snapshot.observedPreventUninstall)
                appendLine(encodeSet(snapshot.observedSelectedAppPackages))
                appendLine(encodeSet(snapshot.observedEmergencyUnlockApps))
                appendLine(snapshot.observedEmergencyUnlockExpireTimeMillis)
                appendLine(snapshot.lastCountdownNotificationExpireTimeMillis)
                appendLine(snapshot.lastCountdownNotificationPostResult.orEmpty())
                appendLine(snapshot.lastWindowStateChangedPackage.orEmpty())
                appendLine(snapshot.lastLaunchedBlockPackage.orEmpty())
                appendLine(snapshot.lastDismissedUninstallPackage.orEmpty())
                appendLine(snapshot.lastLaunchedBlockSource.orEmpty())
                appendLine(snapshot.lastLaunchedRoutineId.orEmpty())
                appendLine(snapshot.lastLaunchedGoalLockId.orEmpty())
                appendLine(snapshot.observedParentModeState.orEmpty())
                appendLine(snapshot.observedParentModeAllowedAppCount)
                appendLine(snapshot.lastRuntimeFlowErrorSource.orEmpty())
                appendLine(snapshot.lastRuntimeFlowErrorType.orEmpty())
                appendLine(snapshot.lastRuntimeFlowRetryAttempt)
                append(snapshot.lastRuntimeFlowRetryDelayMillis)
            },
        )
    }

    private fun stateFile(context: Context): File =
        File(context.filesDir, "keep_accessibility_service_debug_state.txt")

    private fun encodeSet(values: Set<String>): String =
        values.sorted().joinToString("\u001F")

    private fun decodeSet(encoded: String): Set<String> =
        encoded.takeIf { it.isNotBlank() }
            ?.split("\u001F")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
}

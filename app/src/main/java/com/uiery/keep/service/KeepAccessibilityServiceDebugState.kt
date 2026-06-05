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
        val lastCountdownNotificationExpireTimeMillis: Long = 0L,
        val lastCountdownNotificationPostResult: String? = null,
        val lastWindowStateChangedPackage: String? = null,
        val lastLaunchedBlockPackage: String? = null,
        val lastDismissedUninstallPackage: String? = null,
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
            lastDismissedUninstallPackage = parts[if (hasNotificationFields) 10 else 7].ifBlank { null },
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
                append(snapshot.lastDismissedUninstallPackage.orEmpty())
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

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
        val observedSelectedAppPackages: Set<String> = emptySet(),
        val observedEmergencyUnlockApps: Set<String> = emptySet(),
        val lastWindowStateChangedPackage: String? = null,
        val lastLaunchedBlockPackage: String? = null,
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
        if (parts.size < 6) return Snapshot()
        return Snapshot(
            isServiceConnected = parts[0].toBoolean(),
            observedIsKeep = parts[1].toBoolean(),
            observedSelectedAppPackages = decodeSet(parts[2]),
            observedEmergencyUnlockApps = decodeSet(parts[3]),
            lastWindowStateChangedPackage = parts[4].ifBlank { null },
            lastLaunchedBlockPackage = parts[5].ifBlank { null },
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
                appendLine(encodeSet(snapshot.observedSelectedAppPackages))
                appendLine(encodeSet(snapshot.observedEmergencyUnlockApps))
                appendLine(snapshot.lastWindowStateChangedPackage.orEmpty())
                append(snapshot.lastLaunchedBlockPackage.orEmpty())
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

package com.uiery.keep.domain.parentmode

internal data class ParentModeSession(
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val durationMinutes: Int,
    val allowedApps: Set<String>,
    val state: ParentModeSessionState,
)

internal enum class ParentModeSessionState {
    Setup,
    Active,
    Expired,
    UnlockedByPin,
    Cancelled,
}

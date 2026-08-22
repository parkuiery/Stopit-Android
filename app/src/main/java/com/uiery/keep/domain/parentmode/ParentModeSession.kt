package com.uiery.keep.domain.parentmode

internal data class ParentModeSession(
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val durationMinutes: Int,
    val allowedApps: Set<String>,
    val state: ParentModeSessionState,
    /**
     * The guardian PIN this session was started with, as a salted hash.
     *
     * Null only for a session that was already running when this field shipped. Those sessions have
     * nothing to compare against, and refusing to end them would leave the parent holding a locked
     * phone with no way out, so the PIN gate stands aside for them — see
     * [ParentModeGuardianPinVerdict.NoStoredPin].
     */
    val guardianPin: ParentModeGuardianPinDigest? = null,
)

internal enum class ParentModeSessionState {
    Setup,
    Active,
    Expired,
    UnlockedByPin,
    Cancelled,
}

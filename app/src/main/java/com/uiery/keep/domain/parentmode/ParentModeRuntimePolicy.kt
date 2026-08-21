package com.uiery.keep.domain.parentmode

internal object ParentModeRuntimePolicy {
    fun resolveState(
        session: ParentModeSession,
        nowMillis: Long,
    ): ParentModeSessionState = when (session.state) {
        ParentModeSessionState.Active -> if (nowMillis >= session.expiresAtMillis) {
            ParentModeSessionState.Expired
        } else {
            ParentModeSessionState.Active
        }
        else -> session.state
    }

    /**
     * Why the block screen appeared, for the person now holding the phone.
     *
     * Public because the block screen's state carries it; the session itself stays internal.
     */
    fun blockReason(
        session: ParentModeSession?,
        nowMillis: Long,
    ): ParentModeBlockReason? {
        val current = session ?: return null
        return when (resolveState(current, nowMillis)) {
            ParentModeSessionState.Active -> ParentModeBlockReason.AllowedAppsOnly
            ParentModeSessionState.Expired -> ParentModeBlockReason.TimeExpired
            ParentModeSessionState.Setup,
            ParentModeSessionState.UnlockedByPin,
            ParentModeSessionState.Cancelled,
            -> null
        }
    }

    fun shouldBlockPackage(
        session: ParentModeSession,
        packageName: String,
        nowMillis: Long,
    ): Boolean = when (resolveState(session, nowMillis)) {
        ParentModeSessionState.Active -> packageName !in session.allowedApps
        ParentModeSessionState.Expired -> true
        ParentModeSessionState.Setup,
        ParentModeSessionState.UnlockedByPin,
        ParentModeSessionState.Cancelled,
        -> false
    }
}

/**
 * The two things a parent mode block can mean.
 *
 * They are not interchangeable: mid-session the phone is working as agreed and this app simply is
 * not on the list, while after expiry nothing opens at all. A child sent to a parent with the wrong
 * one arrives asking about a problem that does not exist.
 */
enum class ParentModeBlockReason {
    AllowedAppsOnly,
    TimeExpired,
}

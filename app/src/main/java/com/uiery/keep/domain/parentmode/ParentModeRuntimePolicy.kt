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

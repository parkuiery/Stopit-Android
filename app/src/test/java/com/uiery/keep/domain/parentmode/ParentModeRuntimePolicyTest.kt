package com.uiery.keep.domain.parentmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentModeRuntimePolicyTest {
    @Test
    fun activeSessionAllowsOnlyExplicitlyAllowedPackages() {
        val session = activeSession(allowedApps = setOf("com.video.app"))

        assertFalse(
            ParentModeRuntimePolicy.shouldBlockPackage(
                session = session,
                packageName = "com.video.app",
                nowMillis = 2_000L,
            ),
        )
        assertTrue(
            ParentModeRuntimePolicy.shouldBlockPackage(
                session = session,
                packageName = "com.game.app",
                nowMillis = 2_000L,
            ),
        )
    }

    @Test
    fun activeSessionExpiresAtOrAfterExpiryAndBlocksAllowedApps() {
        val session = activeSession(allowedApps = setOf("com.video.app"))

        assertEquals(
            ParentModeSessionState.Active,
            ParentModeRuntimePolicy.resolveState(session, nowMillis = 60_999L),
        )
        assertEquals(
            ParentModeSessionState.Expired,
            ParentModeRuntimePolicy.resolveState(session, nowMillis = 61_000L),
        )
        assertTrue(
            ParentModeRuntimePolicy.shouldBlockPackage(
                session = session,
                packageName = "com.video.app",
                nowMillis = 61_000L,
            ),
        )
    }

    @Test
    fun inactiveSessionStatesDoNotBlockPackages() {
        val inactiveStates = listOf(
            ParentModeSessionState.Setup,
            ParentModeSessionState.UnlockedByPin,
            ParentModeSessionState.Cancelled,
        )

        inactiveStates.forEach { state ->
            assertFalse(
                "state=$state should not block",
                ParentModeRuntimePolicy.shouldBlockPackage(
                    session = activeSession(state = state, allowedApps = setOf("com.video.app")),
                    packageName = "com.game.app",
                    nowMillis = 2_000L,
                ),
            )
        }
    }

    private fun activeSession(
        state: ParentModeSessionState = ParentModeSessionState.Active,
        allowedApps: Set<String>,
    ): ParentModeSession = ParentModeSession(
        startedAtMillis = 1_000L,
        expiresAtMillis = 61_000L,
        durationMinutes = 1,
        allowedApps = allowedApps,
        state = state,
    )
}

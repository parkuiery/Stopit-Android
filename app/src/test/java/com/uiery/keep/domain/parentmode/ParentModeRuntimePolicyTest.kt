package com.uiery.keep.domain.parentmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /**
     * The child holding the phone did not set the session up, so the block screen has to say why it
     * appeared. The two reasons need different words: mid-session the answer is "not this app", and
     * after expiry it is "the agreed time is over" — telling a child the second when the first is
     * true sends them to a parent who has nothing to fix.
     */
    @Test
    fun activeSessionBlocksWithTheAllowedAppsOnlyReasonAndExpiredWithTheTimeExpiredReason() {
        val session = activeSession(allowedApps = setOf("com.video.app"))

        assertEquals(
            ParentModeBlockReason.AllowedAppsOnly,
            ParentModeRuntimePolicy.blockReason(session = session, nowMillis = 2_000L),
        )
        assertEquals(
            ParentModeBlockReason.TimeExpired,
            ParentModeRuntimePolicy.blockReason(session = session, nowMillis = 61_000L),
        )
    }

    @Test
    fun sessionsThatAreNotInForceHaveNoBlockReason() {
        assertNull(ParentModeRuntimePolicy.blockReason(session = null, nowMillis = 2_000L))

        listOf(
            ParentModeSessionState.Setup,
            ParentModeSessionState.UnlockedByPin,
            ParentModeSessionState.Cancelled,
        ).forEach { state ->
            assertNull(
                "state=$state should have no block reason",
                ParentModeRuntimePolicy.blockReason(
                    session = activeSession(state = state, allowedApps = setOf("com.video.app")),
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

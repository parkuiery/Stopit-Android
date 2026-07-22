package com.uiery.keep.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsVpnSessionOwnerTest {
    @Test
    fun oldSessionCannotStopReplacementSession() {
        val owner = DnsVpnSessionOwner()
        val oldSession = owner.startSession()
        val replacementSession = owner.startSession()

        assertFalse(owner.stopIfOwner(oldSession))
        assertTrue(owner.isActive(replacementSession))
        assertTrue(owner.stopIfOwner(replacementSession))
    }

    @Test
    fun replacingSessionRequestsOldWorkerShutdownOnce() {
        val owner = DnsVpnSessionOwner()
        val firstSession = owner.startSession()
        val secondSession = owner.startSession()

        assertTrue(owner.shouldWorkerExit(firstSession))
        assertFalse(owner.shouldWorkerExit(secondSession))
        assertEquals(secondSession, owner.activeSession())
    }
}

package com.uiery.keep.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsVpnSessionOwnerTest {
    @Test
    fun oldSessionCannotStopReplacementSession() {
        val owner = DnsVpnSessionOwner<String>()
        val oldSession = owner.startSession(startId = 10)
        val replacementSession = owner.startSession(startId = 11)

        assertFalse(owner.stopIfOwner(oldSession).shouldStopService)
        assertTrue(owner.isActive(replacementSession))
        assertTrue(owner.stopIfOwner(replacementSession).shouldStopService)
    }

    @Test
    fun replacingSessionRequestsOldWorkerShutdownOnce() {
        val owner = DnsVpnSessionOwner<String>()
        val firstSession = owner.startSession(startId = 10)
        val secondSession = owner.startSession(startId = 11)

        assertTrue(owner.shouldWorkerExit(firstSession))
        assertFalse(owner.shouldWorkerExit(secondSession))
        assertEquals(secondSession, owner.activeSession())
    }

    @Test
    fun staleWorkerStopDoesNotReturnOrClearReplacementWorkerHandle() {
        val owner = DnsVpnSessionOwner<String>()
        val oldSession = owner.startSession(startId = 10)
        owner.publishWorkerHandle(DnsVpnWorkerHandle(oldSession, worker = "old"))
        val replacementSession = owner.startSession(startId = 11)
        val replacementHandle = DnsVpnWorkerHandle(replacementSession, worker = "replacement")
        owner.publishWorkerHandle(replacementHandle)

        val stopResult = owner.stopIfOwner(oldSession)

        assertFalse(stopResult.shouldStopService)
        assertEquals(null, stopResult.workerToShutdown)
        assertEquals(replacementHandle, owner.activeWorkerHandle())
    }

    @Test
    fun stopResultCarriesStartIdOnlyForOwnedActiveSession() {
        val owner = DnsVpnSessionOwner<String>()
        val oldSession = owner.startSession(startId = 10)
        owner.publishWorkerHandle(DnsVpnWorkerHandle(oldSession, worker = "old"))
        val replacementSession = owner.startSession(startId = 11)
        val replacementHandle = DnsVpnWorkerHandle(replacementSession, worker = "replacement")
        owner.publishWorkerHandle(replacementHandle)

        val staleStopResult = owner.stopIfOwner(oldSession)
        val activeStopResult = owner.stopIfOwner(replacementSession)

        assertFalse(staleStopResult.shouldStopService)
        assertEquals(null, staleStopResult.startIdToStop)
        assertEquals(null, staleStopResult.workerToShutdown)
        assertTrue(activeStopResult.shouldStopService)
        assertEquals(11, activeStopResult.startIdToStop)
        assertEquals("replacement", activeStopResult.workerToShutdown)
    }
}

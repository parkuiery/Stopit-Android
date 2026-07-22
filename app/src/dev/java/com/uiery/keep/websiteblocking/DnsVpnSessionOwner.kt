package com.uiery.keep.websiteblocking

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DnsVpnSessionOwner {
    private val nextGeneration = AtomicInteger(0)
    private val activeSession = AtomicReference<DnsVpnSession?>(null)

    fun startSession(): DnsVpnSession {
        val session = DnsVpnSession(nextGeneration.incrementAndGet())
        activeSession.set(session)
        return session
    }

    fun activeSession(): DnsVpnSession? = activeSession.get()

    fun isActive(session: DnsVpnSession): Boolean =
        activeSession.get() == session

    fun shouldWorkerExit(session: DnsVpnSession): Boolean =
        !isActive(session)

    fun stopIfOwner(session: DnsVpnSession): Boolean =
        activeSession.compareAndSet(session, null)

    fun stopActive(): DnsVpnSession? =
        activeSession.getAndSet(null)
}

class DnsVpnSession internal constructor(
    val generation: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is DnsVpnSession && other.generation == generation

    override fun hashCode(): Int = generation
}

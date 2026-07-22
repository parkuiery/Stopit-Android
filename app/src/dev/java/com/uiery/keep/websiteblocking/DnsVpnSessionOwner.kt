package com.uiery.keep.websiteblocking

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DnsVpnSessionOwner<Worker> {
    private val nextGeneration = AtomicInteger(0)
    private val activeSession = AtomicReference<DnsVpnSession?>(null)
    private val activeWorkerHandle = AtomicReference<DnsVpnWorkerHandle<Worker>?>(null)

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

    fun publishWorkerHandle(handle: DnsVpnWorkerHandle<Worker>): Boolean {
        if (!isActive(handle.session)) return false
        activeWorkerHandle.set(handle)
        return true
    }

    fun activeWorkerHandle(): DnsVpnWorkerHandle<Worker>? =
        activeWorkerHandle.get()

    fun stopIfOwner(session: DnsVpnSession): DnsVpnSessionStopResult<Worker> {
        if (!activeSession.compareAndSet(session, null)) {
            return DnsVpnSessionStopResult(shouldStopService = false, workerToShutdown = null)
        }
        val worker = takeWorkerIfOwner(session)
        return DnsVpnSessionStopResult(shouldStopService = true, workerToShutdown = worker)
    }

    fun stopActive(): DnsVpnSessionStopResult<Worker> {
        val session = activeSession.getAndSet(null)
            ?: return DnsVpnSessionStopResult(shouldStopService = false, workerToShutdown = null)
        val worker = takeWorkerIfOwner(session)
        return DnsVpnSessionStopResult(shouldStopService = true, workerToShutdown = worker)
    }

    private fun takeWorkerIfOwner(session: DnsVpnSession): Worker? {
        while (true) {
            val handle = activeWorkerHandle.get() ?: return null
            if (handle.session != session) return null
            if (activeWorkerHandle.compareAndSet(handle, null)) {
                return handle.worker
            }
        }
    }
}

data class DnsVpnWorkerHandle<Worker>(
    val session: DnsVpnSession,
    val worker: Worker,
)

data class DnsVpnSessionStopResult<Worker>(
    val shouldStopService: Boolean,
    val workerToShutdown: Worker?,
)

class DnsVpnSession internal constructor(
    val generation: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is DnsVpnSession && other.generation == generation

    override fun hashCode(): Int = generation
}

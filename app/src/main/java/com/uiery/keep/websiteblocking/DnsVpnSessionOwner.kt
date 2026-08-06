package com.uiery.keep.websiteblocking

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 어느 VPN 세션이 살아 있는 주인인지 가린다.
 *
 * 원자 참조를 쓰지만 그것만으로 안전하지는 않다. [startSession] 과 [publishWorkerHandle] 은
 * 각각은 원자적이어도 그 사이에 다른 세션이 끼어들 수 있어, 뒤늦게 도착한 게시가 새 세션의
 * 핸들을 덮어쓸 수 있다. 그러면 새 워커는 아무도 종료시키지 못한 채 남는다.
 *
 * 그래서 **한 세션의 시작과 워커 게시는 호출부가 같은 락 안에서 이어 붙여야 한다.**
 * `KeepDnsVpnService.startVpnWorker` 가 `lifecycleLock` 으로 그 구간을 감싼다. 락 밖에서
 * 게시하지 말 것.
 */
class DnsVpnSessionOwner<Worker> {
    private val nextGeneration = AtomicInteger(0)
    private val activeSession = AtomicReference<DnsVpnSession?>(null)
    private val activeWorkerHandle = AtomicReference<DnsVpnWorkerHandle<Worker>?>(null)

    fun startSession(startId: Int): DnsVpnSession {
        val session = DnsVpnSession(
            generation = nextGeneration.incrementAndGet(),
            startId = startId,
        )
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
            return DnsVpnSessionStopResult(
                shouldStopService = false,
                workerToShutdown = null,
                startIdToStop = null,
            )
        }
        val worker = takeWorkerIfOwner(session)
        return DnsVpnSessionStopResult(
            shouldStopService = true,
            workerToShutdown = worker,
            startIdToStop = session.startId,
        )
    }

    fun stopActive(): DnsVpnSessionStopResult<Worker> {
        val session = activeSession.getAndSet(null)
            ?: return DnsVpnSessionStopResult(
                shouldStopService = false,
                workerToShutdown = null,
                startIdToStop = null,
            )
        val worker = takeWorkerIfOwner(session)
        return DnsVpnSessionStopResult(
            shouldStopService = true,
            workerToShutdown = worker,
            startIdToStop = session.startId,
        )
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
    val startIdToStop: Int?,
)

class DnsVpnSession internal constructor(
    val generation: Int,
    val startId: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is DnsVpnSession && other.generation == generation

    override fun hashCode(): Int = generation
}

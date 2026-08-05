package com.uiery.keep.domain.websiteblocking

/**
 * 지금 웹 차단 VPN 이 돌고 있어야 하는지에 대한 판단. 잠금 상태와 대상 목록은 서로 다른
 * 비동기 경로로 로드되므로, 아직 다 읽지 못한 시점의 "꺼짐"을 진짜 꺼짐으로 오해하면
 * 방금 시작한 차단을 스스로 껐다 켰다 한다.
 */
sealed interface WebsiteBlockingRuntimeDecision {
    /** 아직 판단할 근거가 부족하다. 아무것도 건드리지 않는다. */
    data object Undecided : WebsiteBlockingRuntimeDecision

    data object Stopped : WebsiteBlockingRuntimeDecision

    data class Running(
        val domains: Set<DomainName>,
        val stopAtEpochMillis: Long?,
    ) : WebsiteBlockingRuntimeDecision
}

/**
 * Android 는 VPN 슬롯을 한 앱만 쥘 수 있게 하고, 새 VPN 이 들어오면 쓰던 VPN 을 끊는다.
 * 실기기 확인 결과 Keep 이 동의를 이미 갖고 있으면 시스템은 아무것도 묻지 않고 상대
 * VPN 을 내려버렸고, 그 VPN 은 잠금이 끝나도 스스로 돌아오지 않았다.
 *
 * 웹사이트를 막으려다 사용자가 쓰던 보호를 말없이 끊는 것은 우리가 할 선택이 아니다.
 */
object WebsiteBlockingConflictPolicy {
    fun shouldAskBeforeDisplacing(
        status: WebsiteBlockingOwnership,
        otherVpnActive: Boolean,
        displacementApproved: Boolean,
    ): Boolean {
        // 이미 우리가 슬롯을 쥐고 있으면 밀어낼 상대가 없다.
        if (status == WebsiteBlockingOwnership.OwnedByKeep) return false
        if (!otherVpnActive) return false
        // 한 번 답한 잠금에 같은 질문을 반복하지 않는다.
        return !displacementApproved
    }
}

/** 지금 VPN 슬롯을 Keep 이 쥐고 있는지. */
enum class WebsiteBlockingOwnership {
    OwnedByKeep,
    NotOwnedByKeep,
}

object WebsiteBlockingRuntimePolicy {
    fun decide(
        runtimeStateLoaded: Boolean,
        isKeep: Boolean,
        hasActiveTimedLock: Boolean,
        timedLockDeadlineMillis: Long?,
        selectedWebDomains: Set<String>,
        routineSession: RoutineWebsiteBlockingSession? = null,
    ): WebsiteBlockingRuntimeDecision {
        if (!runtimeStateLoaded) return WebsiteBlockingRuntimeDecision.Undecided

        val lockDomains = if (isKeep || hasActiveTimedLock) {
            WebsiteBlockingDomainSetPolicy.normalize(selectedWebDomains)
        } else {
            emptySet()
        }
        val domains = lockDomains + routineSession?.domains.orEmpty()
        if (domains.isEmpty()) return WebsiteBlockingRuntimeDecision.Stopped

        return WebsiteBlockingRuntimeDecision.Running(
            domains = domains,
            stopAtEpochMillis = resolveStopAt(
                hasIndefiniteLock = lockDomains.isNotEmpty() && !hasActiveTimedLock,
                // 마감이 있는 것은 시간 잠금뿐이다. 그 마감을 서비스에 넘겨야 홈이 떠 있지
                // 않아도 제때 멈춘다.
                timedLockDeadlineMillis = timedLockDeadlineMillis
                    ?.takeIf { hasActiveTimedLock && lockDomains.isNotEmpty() },
                routineStopAtEpochMillis = routineSession?.stopAtEpochMillis,
            ),
        )
    }

    private fun resolveStopAt(
        hasIndefiniteLock: Boolean,
        timedLockDeadlineMillis: Long?,
        routineStopAtEpochMillis: Long?,
    ): Long? {
        // 수동 잠금은 사용자가 끌 때까지다. 여기에 루틴 마감을 물려주면 잠금이 제풀에 풀린다.
        if (hasIndefiniteLock) return null
        // 겹치는 약속은 가장 늦게 끝나는 쪽까지 유지한다. 먼저 끝나는 쪽에 맞추면 아직
        // 진행 중인 약속이 조용히 깨진다.
        return listOfNotNull(timedLockDeadlineMillis, routineStopAtEpochMillis).maxOrNull()
    }
}

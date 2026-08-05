package com.uiery.keep.domain.websiteblocking

/**
 * 루틴 시간대에 서 있어야 할 웹 차단 한 벌.
 *
 * 루틴은 잠금 세션이 아니라 시간대이므로, 알람이 시작을 알리는 순간 이 시간대가 끝나는
 * 시각까지 함께 넘겨야 한다. 그래야 앱이 떠 있지 않아도 서비스가 제때 스스로 멈춘다.
 */
data class RoutineWebsiteBlockingSession(
    val domains: Set<DomainName>,
    val stopAtEpochMillis: Long,
)

/** 루틴 하나가 이 시각에 웹을 막고 있는지 판단하기 위해 필요한 최소 정보. */
data class RoutineWebsiteWindow(
    val isEnabled: Boolean,
    val isActiveNow: Boolean,
    val endEpochMillis: Long,
    val websites: Set<String>,
)

/** 판정 결과를 옮길 행동 한 가지. */
sealed interface RoutineWebsiteBlockingApplyAction {
    /** 막을 것도 없고 서 있는 것도 없다. 수동 잠금이 세운 차단을 건드리지 않으려면 여기서 멈춰야 한다. */
    data object DoNothing : RoutineWebsiteBlockingApplyAction

    data object StopBlocking : RoutineWebsiteBlockingApplyAction

    /** 막을 것은 있는데 VPN 동의가 없다. 화면이 이 사실을 설명해야 한다. */
    data object ReportConsentMissing : RoutineWebsiteBlockingApplyAction

    data class StartBlocking(
        val session: RoutineWebsiteBlockingSession,
    ) : RoutineWebsiteBlockingApplyAction
}

object RoutineWebsiteBlockingApplyPolicy {
    fun decide(
        session: RoutineWebsiteBlockingSession?,
        hasVpnConsent: Boolean,
        isBlockingStanding: Boolean,
    ): RoutineWebsiteBlockingApplyAction {
        if (session == null) {
            return if (isBlockingStanding) {
                RoutineWebsiteBlockingApplyAction.StopBlocking
            } else {
                RoutineWebsiteBlockingApplyAction.DoNothing
            }
        }
        if (!hasVpnConsent) return RoutineWebsiteBlockingApplyAction.ReportConsentMissing
        return RoutineWebsiteBlockingApplyAction.StartBlocking(session)
    }
}

object RoutineWebsiteBlockingPolicy {
    fun resolveSession(windows: List<RoutineWebsiteWindow>): RoutineWebsiteBlockingSession? {
        val active = windows.filter { it.isEnabled && it.isActiveNow && it.websites.isNotEmpty() }
        if (active.isEmpty()) return null

        val domains = WebsiteBlockingDomainSetPolicy.normalize(
            active.flatMapTo(linkedSetOf()) { it.websites },
        )
        if (domains.isEmpty()) return null

        return RoutineWebsiteBlockingSession(
            domains = domains,
            // 겹치는 루틴이 있으면 가장 늦게 끝나는 시각까지 유지한다. 먼저 끝나는 쪽에
            // 맞추면 아직 진행 중인 루틴의 약속이 조용히 깨진다.
            stopAtEpochMillis = active.maxOf { it.endEpochMillis },
        )
    }
}

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

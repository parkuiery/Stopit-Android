package com.uiery.keep.domain.websiteblocking

sealed interface DnsVpnUpstreamRecovery {
    data class RetryAfter(val delayMillis: Long) : DnsVpnUpstreamRecovery

    data object GiveUp : DnsVpnUpstreamRecovery
}

/**
 * 업스트림 DNS 가 응답하지 않아 필터가 물러난 뒤 다시 서 볼 것인지.
 *
 * 물러나는 순간 서비스를 끝내면 되살릴 주체가 함께 사라진다. 창이 아직 남아 있다면
 * 서비스는 살아서 기다려야 하고, 느린 회선처럼 네트워크가 계속 붙어 있는 경우에는
 * 연결성 콜백이 오지 않으므로 스스로 시계를 보고 다시 시도해야 한다.
 */
object DnsVpnUpstreamRecoveryPolicy {
    fun decide(
        attempt: Int,
        nowEpochMillis: Long,
        stopAtEpochMillis: Long,
    ): DnsVpnUpstreamRecovery {
        val delayMillis = RETRY_DELAYS_MILLIS.getOrElse(attempt) { RETRY_DELAYS_MILLIS.last() }
        // 마감이 없는 잠금(수동)은 0 으로 들어온다. 이걸 "이미 끝남"으로 읽으면 즉시 포기한다.
        if (stopAtEpochMillis <= 0L) return DnsVpnUpstreamRecovery.RetryAfter(delayMillis)
        // 다음 시도가 창 밖에 떨어지면 기다릴 이유가 없다. 마감 종료가 곧 서비스를 내린다.
        if (nowEpochMillis + delayMillis >= stopAtEpochMillis) return DnsVpnUpstreamRecovery.GiveUp
        return DnsVpnUpstreamRecovery.RetryAfter(delayMillis)
    }

    private val RETRY_DELAYS_MILLIS = listOf(5_000L, 15_000L, 60_000L)
}

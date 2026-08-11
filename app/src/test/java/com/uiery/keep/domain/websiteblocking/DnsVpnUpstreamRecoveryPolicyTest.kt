package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 업스트림 DNS 가 응답하지 않아 필터가 물러난 뒤, 다시 서 볼 것인지에 대한 규칙.
 *
 * 지금까지는 물러나는 순간 서비스를 끝냈고, 그러면 되살릴 주체가 함께 사라져 창이 끝날
 * 때까지 웹이 열린 채 남았다. 느린 회선에서는 네트워크가 계속 붙어 있어 연결성 콜백도
 * 오지 않으므로, 스스로 시계를 보고 다시 시도해야 한다.
 */
class DnsVpnUpstreamRecoveryPolicyTest {
    private val windowEnd = 10 * 60 * 1_000L

    @Test
    fun theFirstFailureIsRetriedSoonBecauseItIsUsuallyAHandoverBlip() {
        assertEquals(
            DnsVpnUpstreamRecovery.RetryAfter(5_000L),
            decide(attempt = 0, nowEpochMillis = 0L),
        )
    }

    @Test
    fun repeatedFailuresBackOffInsteadOfSpinning() {
        assertEquals(
            DnsVpnUpstreamRecovery.RetryAfter(15_000L),
            decide(attempt = 1, nowEpochMillis = 0L),
        )
        assertEquals(
            DnsVpnUpstreamRecovery.RetryAfter(60_000L),
            decide(attempt = 2, nowEpochMillis = 0L),
        )
    }

    @Test
    fun theBackoffStopsGrowingSoALongWindowStillGetsCheckedEveryMinute() {
        assertEquals(
            DnsVpnUpstreamRecovery.RetryAfter(60_000L),
            decide(attempt = 9, nowEpochMillis = 0L),
        )
    }

    @Test
    fun aWindowThatHasAlreadyEndedIsNotRetried() {
        assertEquals(
            DnsVpnUpstreamRecovery.GiveUp,
            decide(attempt = 0, nowEpochMillis = windowEnd),
        )
    }

    @Test
    fun aRetryThatWouldLandAfterTheWindowEndsIsNotWorthWaitingFor() {
        // 3초 남았는데 5초 뒤에 다시 보는 것은 창 밖에서 서는 것이다.
        assertEquals(
            DnsVpnUpstreamRecovery.GiveUp,
            decide(attempt = 0, nowEpochMillis = windowEnd - 3_000L),
        )
    }

    @Test
    fun aLockWithoutADeadlineKeepsTryingUntilSomethingElseStopsIt() {
        // 수동 잠금에는 마감이 없다. 마감 0 을 "이미 끝남"으로 읽으면 즉시 포기한다.
        assertEquals(
            DnsVpnUpstreamRecovery.RetryAfter(5_000L),
            DnsVpnUpstreamRecoveryPolicy.decide(
                attempt = 0,
                nowEpochMillis = 9_999_999L,
                stopAtEpochMillis = 0L,
            ),
        )
    }

    private fun decide(attempt: Int, nowEpochMillis: Long) =
        DnsVpnUpstreamRecoveryPolicy.decide(
            attempt = attempt,
            nowEpochMillis = nowEpochMillis,
            stopAtEpochMillis = windowEnd,
        )
}

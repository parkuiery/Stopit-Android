package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 판정 결과를 실제 행동으로 옮기는 규칙. 알람 수신자 안에 있던 분기를 꺼낸 것으로,
 * 부팅·앱 진입에서 같은 판정을 돌릴 때 셋이 서로 다르게 굴면 창마다 다른 약속이 된다.
 */
class RoutineWebsiteBlockingApplyPolicyTest {
    private val session = RoutineWebsiteBlockingSession(
        domains = setOf(DomainName("example.com")),
        stopAtEpochMillis = 1_000L,
    )

    @Test
    fun aStandingWindowWithConsentStartsBlocking() {
        assertEquals(
            RoutineWebsiteBlockingApplyAction.StartBlocking(session),
            RoutineWebsiteBlockingApplyPolicy.decide(
                session = session,
                hasVpnConsent = true,
                isBlockingStanding = false,
            ),
        )
    }

    @Test
    fun aStandingWindowWithoutConsentIsReportedInsteadOfSilentlySkipped() {
        // 수신자에서는 시스템 동의창을 띄울 수 없다. 조용히 건너뛰면 사용자는 웹도
        // 막히고 있다고 믿은 채로 남는다.
        assertEquals(
            RoutineWebsiteBlockingApplyAction.ReportConsentMissing,
            RoutineWebsiteBlockingApplyPolicy.decide(
                session = session,
                hasVpnConsent = false,
                isBlockingStanding = false,
            ),
        )
    }

    @Test
    fun noWindowStopsBlockingThatIsStillStanding() {
        // 창 도중 루틴을 끄거나 지운 경우다. 그대로 두면 약속이 끝났는데도 계속 막는다.
        assertEquals(
            RoutineWebsiteBlockingApplyAction.StopBlocking,
            RoutineWebsiteBlockingApplyPolicy.decide(
                session = null,
                hasVpnConsent = true,
                isBlockingStanding = true,
            ),
        )
    }

    @Test
    fun noWindowAndNothingStandingTouchesNothing() {
        // 매번 stop 을 던지면 수동 잠금이 세운 차단까지 흔든다.
        assertEquals(
            RoutineWebsiteBlockingApplyAction.DoNothing,
            RoutineWebsiteBlockingApplyPolicy.decide(
                session = null,
                hasVpnConsent = true,
                isBlockingStanding = false,
            ),
        )
    }

    @Test
    fun consentIsOnlyAskedAboutWhenThereIsSomethingToBlock() {
        assertEquals(
            RoutineWebsiteBlockingApplyAction.DoNothing,
            RoutineWebsiteBlockingApplyPolicy.decide(
                session = null,
                hasVpnConsent = false,
                isBlockingStanding = false,
            ),
        )
    }
}

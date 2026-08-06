package com.uiery.keep.websiteblocking

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 업스트림이 죽었을 때 서비스가 스스로 끝나면, 되살릴 주체가 그 순간 함께 사라진다.
 * 네트워크 콜백을 해제하고 stopSelf 까지 가버리므로 회선이 돌아와도 아무도 다시 세우지 않는다.
 * 창이 남아 있는 동안에는 서비스가 살아서 기다려야 한다.
 */
class KeepDnsVpnServiceRecoveryWiringTest {
    private val source =
        File("src/main/java/com/uiery/keep/websiteblocking/KeepDnsVpnService.kt").readText()

    @Test
    fun theServiceRemembersTheWindowEndSoRecoveryKnowsWhenToQuit() {
        assertTrue(
            "마감을 기억하지 않으면 창이 끝난 뒤에도 재시도를 계속한다",
            Regex("""private var stopAtEpochMillis""").containsMatchIn(source),
        )
    }

    @Test
    fun upstreamFailureConsultsTheRecoveryPolicyInsteadOfQuittingOutright() {
        assertTrue(
            "재시도 여부는 정책이 정한다",
            source.contains("DnsVpnUpstreamRecoveryPolicy.decide("),
        )
    }

    @Test
    fun theServiceOnlyQuitsWhenThePolicySaysTheWindowIsOver() {
        val recovery = recoverySection()
        val giveUp = recovery.indexOf("GiveUp")
        val stop = recovery.indexOf("stopFromWorker(")

        assertTrue("복구 경로가 GiveUp 을 판단해야 한다", giveUp >= 0)
        assertTrue("복구 경로에서 서비스를 끝내는 곳이 있어야 한다", stop >= 0)
        assertTrue(
            "GiveUp 이 아닌데 서비스를 끝내면 회선이 돌아와도 되살아나지 못한다",
            giveUp < stop,
        )
    }

    @Test
    fun aRecoverableFailureSchedulesAnotherAttemptWithoutTearingTheServiceDown() {
        val recovery = recoverySection()

        assertTrue(
            "다음 시도를 예약해야 한다. 느린 회선은 연결성 콜백을 주지 않는다",
            recovery.contains("postDelayed("),
        )
        assertTrue(
            "다시 설 때는 기존 바인딩 경로를 그대로 쓴다",
            recovery.contains("bindingCoordinator.retryBinding("),
        )
        assertTrue(
            "복구를 기다리는 동안 네트워크 콜백을 해제하면 안 된다",
            !recovery.contains("unregisterUnderlyingNetworkCallback("),
        )
    }

    @Test
    fun theRetryBudgetIsRefilledByAWorkingUpstreamNotByAStandingTun() {
        // 실기기(SM-G991N, 업스트림 손실 85%)에서 잡힌 결함이다. TUN 수립 시점에 예산을
        // 되돌리면, 매번 250ms 빠른 재시도만 반복되고 백오프는 영영 걸리지 않는다.
        // Active 는 "TUN 이 섰다"이지 "업스트림이 살아났다"가 아니다.
        val active = source.indexOf("WebsiteBlockingStatus.Active")
        val activeBlock = source.substring(active, minOf(active + 300, source.length))
        assertTrue(
            "TUN 이 섰다는 이유로 재시도 예산을 되돌리면 안 된다",
            !activeBlock.contains("recoveryAttempt = 0"),
        )
        assertTrue(
            "예산은 업스트림 왕복이 실제로 성공했을 때 되돌아와야 한다",
            source.contains("markUpstreamHealthy()"),
        )
    }

    /** 복구 판단 함수 본문만 떼어 본다. 파일 전체를 보면 다른 경로의 stop 과 섞인다. */
    private fun recoverySection(): String {
        val start = source.indexOf("private fun recoverOrStopFromWorker(")
        assertTrue("복구 판단이 별도 함수로 있어야 한다", start >= 0)
        val next = source.indexOf("\n    private fun ", start + 1)
        return source.substring(start, if (next > start) next else source.length)
    }
}

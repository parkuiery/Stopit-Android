package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Test

class WebsiteBlockingRuntimePolicyTest {
    @Test
    fun stateIsNotJudgedBeforeItIsLoaded() {
        // 로딩 전의 기본값(꺼짐)을 진짜 꺼짐으로 읽으면 방금 시작한 차단을 스스로 내린다.
        assertEquals(
            WebsiteBlockingRuntimeDecision.Undecided,
            decide(runtimeStateLoaded = false, isKeep = true, selectedWebDomains = setOf("example.com")),
        )
    }

    @Test
    fun manualKeepRunsWithoutADeadline() {
        assertEquals(
            WebsiteBlockingRuntimeDecision.Running(
                domains = setOf(DomainName("example.com")),
                stopAtEpochMillis = null,
            ),
            decide(isKeep = true, selectedWebDomains = setOf("example.com")),
        )
    }

    @Test
    fun timedLockHandsItsDeadlineToTheService() {
        // 홈이 떠 있지 않아도 잠금은 제때 풀려야 한다.
        assertEquals(
            WebsiteBlockingRuntimeDecision.Running(
                domains = setOf(DomainName("example.com")),
                stopAtEpochMillis = 1_700_000_000_000L,
            ),
            decide(
                hasActiveTimedLock = true,
                timedLockDeadlineMillis = 1_700_000_000_000L,
                selectedWebDomains = setOf("example.com"),
            ),
        )
    }

    @Test
    fun manualKeepIgnoresALeftoverTimedLockDeadline() {
        assertEquals(
            WebsiteBlockingRuntimeDecision.Running(
                domains = setOf(DomainName("example.com")),
                stopAtEpochMillis = null,
            ),
            decide(
                isKeep = true,
                hasActiveTimedLock = false,
                timedLockDeadlineMillis = 1_700_000_000_000L,
                selectedWebDomains = setOf("example.com"),
            ),
        )
    }

    @Test
    fun noLockOrNoWebsiteTargetsMeansNoVpn() {
        assertEquals(
            WebsiteBlockingRuntimeDecision.Stopped,
            decide(isKeep = false, selectedWebDomains = setOf("example.com")),
        )
        // 앱만 고른 잠금에 VPN 을 세우면 아무것도 막지 않으면서 권한만 요구하게 된다.
        assertEquals(
            WebsiteBlockingRuntimeDecision.Stopped,
            decide(isKeep = true, selectedWebDomains = emptySet()),
        )
    }

    @Test
    fun storedGarbageDomainsDoNotStartAVpnThatBlocksNothing() {
        assertEquals(
            WebsiteBlockingRuntimeDecision.Stopped,
            decide(isKeep = true, selectedWebDomains = setOf("   ", "not a domain")),
        )
    }

    private fun decide(
        runtimeStateLoaded: Boolean = true,
        isKeep: Boolean = false,
        hasActiveTimedLock: Boolean = false,
        timedLockDeadlineMillis: Long? = null,
        selectedWebDomains: Set<String>,
    ): WebsiteBlockingRuntimeDecision =
        WebsiteBlockingRuntimePolicy.decide(
            runtimeStateLoaded = runtimeStateLoaded,
            isKeep = isKeep,
            hasActiveTimedLock = hasActiveTimedLock,
            timedLockDeadlineMillis = timedLockDeadlineMillis,
            selectedWebDomains = selectedWebDomains,
        )
}

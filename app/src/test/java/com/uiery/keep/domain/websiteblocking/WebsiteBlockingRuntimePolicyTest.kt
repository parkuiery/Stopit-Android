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

    @Test
    fun aRoutineWindowStandsOnItsOwnWithoutAManualLock() {
        // 알람이 지연·누락되었거나 창 도중 재부팅한 회차는 앱을 열었을 때라도 서야 한다.
        assertEquals(
            WebsiteBlockingRuntimeDecision.Running(
                domains = setOf(DomainName("routine.com")),
                stopAtEpochMillis = 1_700_000_000_000L,
            ),
            decide(
                selectedWebDomains = emptySet(),
                routineSession = session("routine.com", 1_700_000_000_000L),
            ),
        )
    }

    @Test
    fun aLockAndARoutineBlockBothTargetSets() {
        // 한쪽만 세우면 다른 쪽 약속이 조용히 깨진다.
        assertEquals(
            WebsiteBlockingRuntimeDecision.Running(
                domains = setOf(DomainName("example.com"), DomainName("routine.com")),
                stopAtEpochMillis = 1_700_000_000_000L,
            ),
            decide(
                hasActiveTimedLock = true,
                timedLockDeadlineMillis = 1_600_000_000_000L,
                selectedWebDomains = setOf("example.com"),
                routineSession = session("routine.com", 1_700_000_000_000L),
            ),
        )
    }

    @Test
    fun overlappingDeadlinesHoldUntilTheLastOneEnds() {
        assertEquals(
            1_800_000_000_000L,
            (
                decide(
                    hasActiveTimedLock = true,
                    timedLockDeadlineMillis = 1_800_000_000_000L,
                    selectedWebDomains = setOf("example.com"),
                    routineSession = session("routine.com", 1_700_000_000_000L),
                ) as WebsiteBlockingRuntimeDecision.Running
            ).stopAtEpochMillis,
        )
    }

    @Test
    fun anIndefiniteManualKeepOutlastsAnyRoutineDeadline() {
        // 수동 잠금은 사용자가 끌 때까지다. 루틴 마감을 물려주면 잠금이 제풀에 풀린다.
        assertEquals(
            WebsiteBlockingRuntimeDecision.Running(
                domains = setOf(DomainName("example.com"), DomainName("routine.com")),
                stopAtEpochMillis = null,
            ),
            decide(
                isKeep = true,
                selectedWebDomains = setOf("example.com"),
                routineSession = session("routine.com", 1_700_000_000_000L),
            ),
        )
    }

    @Test
    fun aRoutineThatIsNoLongerStandingStopsTheBlocking() {
        // 창 도중 루틴을 끄거나 지운 경우다. 앱 진입 판정이 반대 방향도 정리해야 한다.
        assertEquals(
            WebsiteBlockingRuntimeDecision.Stopped,
            decide(selectedWebDomains = emptySet(), routineSession = null),
        )
    }

    private fun session(domain: String, stopAtEpochMillis: Long) =
        RoutineWebsiteBlockingSession(
            domains = setOf(DomainName(domain)),
            stopAtEpochMillis = stopAtEpochMillis,
        )

    private fun decide(
        runtimeStateLoaded: Boolean = true,
        isKeep: Boolean = false,
        hasActiveTimedLock: Boolean = false,
        timedLockDeadlineMillis: Long? = null,
        selectedWebDomains: Set<String>,
        routineSession: RoutineWebsiteBlockingSession? = null,
    ): WebsiteBlockingRuntimeDecision =
        WebsiteBlockingRuntimePolicy.decide(
            runtimeStateLoaded = runtimeStateLoaded,
            isKeep = isKeep,
            hasActiveTimedLock = hasActiveTimedLock,
            timedLockDeadlineMillis = timedLockDeadlineMillis,
            selectedWebDomains = selectedWebDomains,
            routineSession = routineSession,
        )
}

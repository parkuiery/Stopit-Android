package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineWebsiteBlockingPolicyTest {
    @Test
    fun activeRoutineBlocksItsOwnWebsitesUntilItsWindowEnds() {
        val session = RoutineWebsiteBlockingPolicy.resolveSession(
            listOf(window(websites = setOf("example.com"), endEpochMillis = 1_000L)),
        )

        assertEquals(setOf(DomainName("example.com")), session?.domains)
        assertEquals(1_000L, session?.stopAtEpochMillis)
    }

    @Test
    fun overlappingRoutinesHoldUntilTheLastOneEnds() {
        // 먼저 끝나는 루틴에 맞추면 아직 진행 중인 루틴의 약속이 조용히 깨진다.
        val session = RoutineWebsiteBlockingPolicy.resolveSession(
            listOf(
                window(websites = setOf("example.com"), endEpochMillis = 1_000L),
                window(websites = setOf("example.org"), endEpochMillis = 5_000L),
            ),
        )

        assertEquals(
            setOf(DomainName("example.com"), DomainName("example.org")),
            session?.domains,
        )
        assertEquals(5_000L, session?.stopAtEpochMillis)
    }

    @Test
    fun routinesThatAreNotBlockingRightNowAreIgnored() {
        assertNull(
            RoutineWebsiteBlockingPolicy.resolveSession(
                listOf(window(websites = setOf("example.com"), isActiveNow = false)),
            ),
        )
        assertNull(
            RoutineWebsiteBlockingPolicy.resolveSession(
                listOf(window(websites = setOf("example.com"), isEnabled = false)),
            ),
        )
    }

    @Test
    fun anAppOnlyRoutineDoesNotStartAVpnThatBlocksNothing() {
        assertNull(RoutineWebsiteBlockingPolicy.resolveSession(listOf(window(websites = emptySet()))))
    }

    @Test
    fun storedGarbageIsNotTreatedAsATarget() {
        // 마이그레이션 기본값이나 손상된 캐시가 빈 항목으로 남을 수 있다.
        assertNull(
            RoutineWebsiteBlockingPolicy.resolveSession(
                listOf(window(websites = setOf("   ", "not a domain"))),
            ),
        )
    }

    private fun window(
        websites: Set<String>,
        isEnabled: Boolean = true,
        isActiveNow: Boolean = true,
        endEpochMillis: Long = 1_000L,
    ) = RoutineWebsiteWindow(
        isEnabled = isEnabled,
        isActiveNow = isActiveNow,
        endEpochMillis = endEpochMillis,
        websites = websites,
    )
}

package com.uiery.keep.websiteblocking

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 업스트림 타임아웃 예산.
 *
 * 실기기(Galaxy S21, 2026-08-05) 회선은 1.1.1.1 왕복이 약 1.7초였고, 타임아웃이 1.5초라
 * 모든 질의가 시간 초과로 떨어져 필터가 상시 물러났다. 느린 회선을 "죽은 업스트림"으로
 * 읽으면 차단은 그 회선에서 영원히 서지 못한다.
 *
 * 이 값을 올려도 차단 정확도는 변하지 않는다. 차단 대상은 NXDOMAIN 을 로컬에서 만들고
 * (DnsVpnDatagramProcessor 의 DnsBlockDecision.Block), 업스트림은 통과 도메인에만 관여한다.
 */
class DnsUpstreamTimeoutBudgetTest {
    private val source =
        File("src/main/java/com/uiery/keep/websiteblocking/KeepDnsVpnService.kt").readText()

    @Test
    fun theUpstreamTimeoutLeavesRoomForASlowLine() {
        val timeout = requireNotNull(
            Regex("""DNS_TIMEOUT_MILLIS = ([\d_]+)""")
                .find(source)
                ?.groupValues
                ?.get(1)
                ?.replace("_", "")
                ?.toInt(),
        ) { "업스트림 타임아웃 상수를 찾지 못했다" }

        assertTrue(
            "측정된 1.7초 회선보다 짧으면 그 회선에서는 차단이 상시 물러난다 (현재 ${timeout}ms)",
            timeout >= 3_000,
        )
    }
}

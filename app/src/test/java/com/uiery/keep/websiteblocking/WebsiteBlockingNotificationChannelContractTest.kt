package com.uiery.keep.websiteblocking

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 알림 채널 ID 는 프로덕션 출시 순간 고정된다.
 *
 * 바꾸면 Android 는 새 채널을 만들고, 사용자가 기존 채널에 해둔 설정(끄기·중요도·소리)은
 * 아무도 보지 않는 채널에 남는다. 알림을 껐던 사용자에게 알림이 다시 뜨기 시작하는데, 그건
 * 사용자가 명시적으로 거절한 것을 되돌리는 일이다.
 *
 * 이 값은 스파이크 시절 `website_blocking_spike` 였고 prod 출시 직전 마지막으로 정리했다.
 * 그 뒤로는 바꿀 수 없으므로 테스트로 못박는다.
 */
class WebsiteBlockingNotificationChannelContractTest {
    private val service =
        File("src/main/java/com/uiery/keep/websiteblocking/KeepDnsVpnService.kt").readText()

    @Test
    fun theChannelIdIsFrozen() {
        assertTrue(
            "채널 ID 는 출시 이후 바꿀 수 없다. 바꾸려면 사용자 알림 설정이 고아가 되는 대가를 " +
                "먼저 검토하고 이 테스트를 함께 고쳐야 한다.",
            service.contains("""private const val CHANNEL_ID = "website_blocking""""),
        )
    }

    @Test
    fun noSpikeNamingSurvivesInTheShippedService() {
        // 동작하는 이름만 본다. 옛 값이 왜 바뀌었는지 적어둔 주석은 남아야 하고, 그것까지
        // 금지하면 이력을 지우는 대가로 테스트를 통과시키게 된다.
        for (identifier in listOf(
            """CHANNEL_ID = "website_blocking_spike"""",
            "R.string.website_blocking_spike_",
            """DIAGNOSTIC_TAG = "KeepDnsVpnSpike"""",
            "startForegroundForSpike",
        )) {
            assertTrue(
                "출시 서비스에 스파이크 시절 식별자가 남으면 안 된다: $identifier",
                !service.contains(identifier),
            )
        }
    }

    @Test
    fun theFreezeReasonIsRecordedNextToTheValue() {
        val idx = service.indexOf("private const val CHANNEL_ID")
        assertTrue("채널 ID 선언이 있어야 한다", idx >= 0)
        val preceding = service.substring(maxOf(0, idx - 700), idx)
        assertTrue(
            "왜 못 바꾸는지가 값 옆에 적혀 있어야 한다. 이유 없는 상수는 결국 바뀐다.",
            "채널 ID" in preceding && "새 채널" in preceding,
        )
    }
}

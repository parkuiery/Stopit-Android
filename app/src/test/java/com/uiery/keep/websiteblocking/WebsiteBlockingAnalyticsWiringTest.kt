package com.uiery.keep.websiteblocking

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 웹 차단은 사용자가 승인해야만 서고 다른 VPN 하나에 밀려 조용히 내려간다. "잠금이 켜졌다"와
 * "웹사이트가 실제로 막혔다"의 차이를 출시 후에 판별할 수 있는 신호는 이 셋뿐이라, 배선이
 * 끊기면 문제를 크래시 로그로만 쫓게 된다.
 *
 * 두 가지를 특히 고정한다. 세지 말아야 할 것을 세면 지표가 조용히 거짓말을 하기 때문이다.
 */
class WebsiteBlockingAnalyticsWiringTest {
    private val controller =
        File("src/main/java/com/uiery/keep/websiteblocking/WebsiteBlockingVpnController.kt").readText()
    private val screen =
        File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()
    private val viewModel =
        File("src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt").readText()

    @Test
    fun homeCarriesAllThreeSignalsToAnalytics() {
        for (callback in listOf(
            "onConsentResult",
            "onVpnConflictResolved",
            "onStatusChanged",
        )) {
            assertTrue("컨트롤러가 $callback 을 노출해야 한다", controller.contains("$callback:"))
            assertTrue("홈이 $callback 을 연결해야 한다", screen.contains("$callback = viewModel::"))
        }
        for (tracker in listOf(
            "trackWebsiteBlockingConsentResult",
            "trackWebsiteBlockingVpnConflictResolved",
            "trackWebsiteBlockingStatusChanged",
        )) {
            assertTrue("$tracker 가 analytics 로 이어져야 한다", viewModel.contains("analytics.$tracker("))
        }
    }

    @Test
    fun aConsentDialogWeDidNotRaiseIsNotCountedAsAnAnswer() {
        // Ignore 는 우리가 띄우지 않은 결과다. 그것까지 세면 거부율이 실제보다 커지고,
        // 기능이 도달하지 못했다는 잘못된 결론으로 이어진다.
        val guard = controller.indexOf("currentOnConsentResult(granted)")
        assertTrue("동의 결과를 전달해야 한다", guard >= 0)
        assertTrue(
            "Ignore 결과는 세지 않아야 한다",
            controller.substring(maxOf(0, guard - 400), guard)
                .contains("WebsiteBlockingConsentOutcome.Ignore"),
        )
    }

    @Test
    fun statusIsCountedAsTransitionsNotAsDwellTime() {
        // 화면에 붙을 때마다 현재 상태를 다시 실으면 같은 상태가 화면 진입 횟수만큼 쌓인다.
        // 그러면 "차단이 몇 번 끊겼나"가 아니라 "홈에 몇 번 들어왔나"를 세게 된다.
        // `status.value` 동기 조회가 앞에 여러 번 나오므로 흐름을 구독하는 자리만 집는다.
        val observer = controller.indexOf("WebsiteBlockingRuntimeState.status\n")
        assertTrue("상태 흐름을 구독해야 한다", observer >= 0)
        val body = controller.substring(observer, minOf(observer + 400, controller.length))
        assertTrue("첫 방출은 건너뛰어야 한다", body.contains("drop(1)"))
        assertTrue("같은 상태 반복은 세지 않아야 한다", body.contains("distinctUntilChanged()"))
    }

    @Test
    fun noEventCarriesWhatTheUserBlocked() {
        // docs/WEBSITE_BLOCKING_VPN_SPIKE.md 의 Privacy Rules. 차단 도메인은 개수조차 싣지
        // 않는다. 무엇을 막았는지가 드러나면 그건 브라우징 성향 그 자체다.
        val analytics =
            File("src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt").readText()
        val start = analytics.indexOf("trackWebsiteBlockingConsentResult")
        assertTrue("웹 차단 계측 구현이 있어야 한다", start >= 0)
        val end = analytics.indexOf("trackFirstCoreActionCompleted", start)
        val websiteBlockingBlock = analytics.substring(start, end)

        for (forbidden in listOf("domain", "Domain", "host", "Host", "url", "Url")) {
            assertTrue(
                "웹 차단 이벤트에 '$forbidden' 이 실리면 안 된다",
                !websiteBlockingBlock.contains(forbidden),
            )
        }
    }
}

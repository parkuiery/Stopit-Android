package com.uiery.keep.websiteblocking

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 웹 차단은 사용자가 승인해야만 서고 다른 VPN 하나에 밀려 조용히 내려간다. "잠금이 켜졌다"와
 * "웹사이트가 실제로 막혔다"의 차이를 출시 후에 판별할 수 있는 신호는 이것뿐이라, 배선이
 * 끊기면 문제를 크래시 로그로만 쫓게 된다.
 *
 * 세지 말아야 할 것을 세거나, 세야 할 자리를 화면 수명에 묶으면 지표가 조용히 거짓말을 한다.
 * 그 네 자리를 고정한다.
 */
class WebsiteBlockingAnalyticsWiringTest {
    private val controller =
        File("src/main/java/com/uiery/keep/websiteblocking/WebsiteBlockingVpnController.kt").readText()
    private val reporter =
        File("src/main/java/com/uiery/keep/websiteblocking/WebsiteBlockingStatusReporter.kt").readText()
    private val application =
        File("src/main/java/com/uiery/keep/KeepApplication.kt").readText()
    private val routineLauncher =
        File("src/main/java/com/uiery/keep/websiteblocking/AndroidRoutineWebsiteBlockingLauncher.kt").readText()
    private val screen =
        File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()
    private val viewModel =
        File("src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt").readText()

    @Test
    fun theTwoScreenOnlyMomentsAreCarriedToAnalytics() {
        // 시스템 동의창과 충돌 확인 대화상자는 화면에서만 뜰 수 있다. 이 둘만 컨트롤러가 쥔다.
        for (callback in listOf("onConsentResult", "onVpnConflictResolved")) {
            assertTrue("컨트롤러가 $callback 을 노출해야 한다", controller.contains("$callback:"))
            assertTrue("홈이 $callback 을 연결해야 한다", screen.contains("$callback = viewModel::"))
        }
        for (tracker in listOf(
            "trackWebsiteBlockingConsentResult",
            "trackWebsiteBlockingVpnConflictResolved",
        )) {
            assertTrue("$tracker 가 analytics 로 이어져야 한다", viewModel.contains("analytics.$tracker("))
        }
    }

    @Test
    fun statusIsObservedForTheProcessNotForOneScreen() {
        // 이 관찰이 홈에 붙어 있으면 홈이 떠 있는 동안만 세게 된다. 정작 놓치면 안 되는
        // 전환은 사용자가 홈을 보고 있지 않을 때 일어난다 — 루틴이 세운 세션이 다른 VPN 에
        // 밀리거나, 업스트림이 죽어 필터가 스스로 물러나는 순간이다.
        assertTrue(
            "상태 관찰은 컨트롤러가 아니라 프로세스 리포터가 소유해야 한다",
            !controller.contains("trackWebsiteBlockingStatusChanged") &&
                !controller.contains("onStatusChanged"),
        )
        assertTrue(
            "리포터가 런타임 상태 흐름을 구독해야 한다",
            reporter.contains("WebsiteBlockingRuntimeState.status"),
        )
        assertTrue(
            "리포터가 analytics 로 이어져야 한다",
            reporter.contains("analytics.trackWebsiteBlockingStatusChanged("),
        )
        assertTrue(
            "프로세스 수명 스코프에서 시작해야 한다",
            application.contains("websiteBlockingStatusReporter.start(applicationScope)"),
        )
    }

    @Test
    fun statusIsCountedAsTransitionsNotAsDwellTime() {
        // 프로세스가 깨어날 때마다 현재 상태를 다시 실으면 "차단이 몇 번 끊겼나"가 아니라
        // "프로세스가 몇 번 떴나"를 세게 된다.
        assertTrue("첫 방출은 건너뛰어야 한다", reporter.contains("drop(1)"))
        assertTrue("같은 상태 반복은 세지 않아야 한다", reporter.contains("distinctUntilChanged()"))
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
    fun theRoutinePathReportsItsOwnSessionsIncludingTheSilentFailure() {
        // 루틴은 화면 없이 돈다. 창은 열렸는데 동의가 없어 웹이 뚫린 회차는 사용자도 우리도
        // 모르는 채로 지나간다. 이 경로가 계측되지 않으면 그 실패는 영원히 보이지 않는다.
        assertTrue(
            "루틴 런처가 analytics 로 이어져야 한다",
            routineLauncher.contains("analytics.trackWebsiteBlockingRoutineSession("),
        )
        for (outcome in listOf("STARTED", "STOPPED", "CONSENT_MISSING")) {
            assertTrue(
                "$outcome 회차를 기록해야 한다",
                routineLauncher.contains("AnalyticsWebsiteBlockingRoutineOutcome.$outcome"),
            )
        }
        assertTrue(
            "무엇이 세션을 세웠는지 실어야 알람 경로의 고장을 구분할 수 있다",
            routineLauncher.contains("trigger = trigger.name.lowercase()"),
        )

        // 아무것도 하지 않은 회차는 세지 않는다. 알람과 부팅은 창 밖에서도 돌기 때문에,
        // 그것까지 세면 판정 횟수가 실제 세션 신호를 덮는다.
        val doNothing = routineLauncher.indexOf("RoutineWebsiteBlockingApplyAction.DoNothing")
        assertTrue("DoNothing 분기가 있어야 한다", doNothing >= 0)
        val branch = routineLauncher.substring(doNothing, minOf(doNothing + 120, routineLauncher.length))
        assertTrue("DoNothing 은 기록하지 않아야 한다", !branch.contains("report("))
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
        assertTrue("웹 차단 구현 블록의 끝을 찾아야 한다", end > start)
        val websiteBlockingBlock = analytics.substring(start, end)

        for (forbidden in listOf("domain", "Domain", "host", "Host", "url", "Url")) {
            assertTrue(
                "웹 차단 이벤트에 '$forbidden' 이 실리면 안 된다",
                !websiteBlockingBlock.contains(forbidden),
            )
        }
    }
}

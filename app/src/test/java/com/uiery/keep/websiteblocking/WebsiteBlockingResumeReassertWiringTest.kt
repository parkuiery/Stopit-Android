package com.uiery.keep.websiteblocking

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실기기(SM-G991N)에서 잡힌 구멍이다. 창 안에서 서비스만 죽은 뒤 홈으로 돌아와도 판정이
 * 계속 `Running` 이라 효과가 다시 돌지 않았고, 차단은 서지 않은 채 남았다.
 *
 * 재확인은 상태 변화가 아니라 사용자가 돌아오는 행동 한 번에 묶여야 한다. 런타임 상태를
 * 그대로 효과 키에 넣으면 백오프 중인 서비스를 홈이 계속 다시 세우게 된다.
 */
class WebsiteBlockingResumeReassertWiringTest {
    private val controller =
        File("src/main/java/com/uiery/keep/websiteblocking/WebsiteBlockingVpnController.kt").readText()
    private val screen =
        File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()

    @Test
    fun homeTellsTheControllerWhenTheUserCameBack() {
        val resume = screen.indexOf("Lifecycle.Event.ON_RESUME")
        assertTrue("ON_RESUME 훅이 있어야 한다", resume >= 0)
        assertTrue(
            "돌아온 사실을 컨트롤러에 전달해야 한다",
            screen.substring(resume, minOf(resume + 900, screen.length)).contains("resumeCount"),
        )
        assertTrue(
            "컨트롤러가 그 신호를 받아야 한다",
            controller.contains("resumeCount: Int"),
        )
    }

    @Test
    fun theReassertIsKeyedOnComingBackNotOnRuntimeState() {
        // 상태를 키로 쓰면 NetworkUnavailable 로 물러난 세션을 홈이 즉시 다시 세워
        // 서비스의 백오프와 싸운다.
        assertTrue(
            "재확인 효과는 재개 신호를 키로 삼아야 한다",
            Regex("""LaunchedEffect\(resumeCount\)""").containsMatchIn(controller),
        )
        assertTrue(
            "무엇을 다시 세울지는 정책이 정한다",
            controller.contains("WebsiteBlockingReassertPolicy.shouldReassertOnResume("),
        )
    }

    @Test
    fun comingBackNeverRaisesTheConsentDialog() {
        // 동의창은 사용자가 잠금을 시작할 때만 뜬다. 홈에 돌아올 때마다 뜨면 괴롭힘이다.
        val reassert = reassertSection()
        assertTrue(
            "동의가 없으면 조용히 물러나야 한다",
            reassert.contains("VpnService.prepare("),
        )
        assertTrue(
            "재확인 경로에서 동의창을 띄우면 안 된다",
            !reassert.contains("consentLauncher.launch("),
        )
        assertTrue(
            "동의가 있을 때만 다시 세운다",
            reassert.contains("startWebsiteBlocking("),
        )
    }

    private fun reassertSection(): String {
        val start = controller.indexOf("LaunchedEffect(resumeCount)")
        assertTrue("재확인 효과가 있어야 한다", start >= 0)
        val next = controller.indexOf("\n    LaunchedEffect(", start + 1)
        val end = if (next > start) next else controller.indexOf("\n    pendingDisplacement", start)
        return controller.substring(start, if (end > start) end else controller.length)
    }
}

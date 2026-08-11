package com.uiery.keep.websiteblocking

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 시간 잠금 사용자는 잠금 화면에 머무른다. 그 화면의 웹 차단 경고에 복구 버튼이 없으면,
 * 동의를 거부했거나 다른 VPN 에 밀린 잠금은 끝날 때까지 되살릴 방법이 없다. 자동 경로는 한 번
 * 거부한 잠금에 동의창을 다시 띄우지 않기 때문이다(의도된 괴롭힘 방지).
 *
 * 버튼을 붙이지 못했던 이유는 "권한만 받아서는 필터가 서지 않는다"였다. 실제로 세우는 일이
 * 컨트롤러 안에 있었고 그 컨트롤러는 홈에만 있었다. 그래서 세우는 부분을 화면 밖으로 꺼냈다.
 */
class WebsiteBlockingLockScreenRecoveryWiringTest {
    private val asserter =
        File("src/main/java/com/uiery/keep/websiteblocking/AndroidWebsiteBlockingAsserter.kt").readText()
    private val controller =
        File("src/main/java/com/uiery/keep/websiteblocking/WebsiteBlockingVpnController.kt").readText()
    private val lockScreen =
        File("src/main/java/com/uiery/keep/feature/lock/LockScreen.kt").readText()
    private val lockViewModel =
        File("src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt").readText()

    @Test
    fun theLockScreenBannerCanActuallyBringTheFilterBack() {
        assertTrue(
            "잠금 화면 배너에 복구 경로가 연결돼야 한다",
            lockScreen.contains("onConsentGranted = viewModel::retryWebsiteBlocking"),
        )
        assertTrue(
            "복구는 화면 밖 asserter 가 수행해야 한다",
            lockViewModel.contains("websiteBlockingAsserter.assertAfterConsent("),
        )
    }

    @Test
    fun thereIsOnlyOnePlaceThatStartsTheService() {
        // 시작 경로가 두 벌이 되면 한쪽만 고쳐진 채 남는다. 컨트롤러도 같은 프리미티브를 쓴다.
        assertTrue(
            "시작 인텐트를 만드는 공유 프리미티브가 asserter 구현 파일에 있어야 한다",
            asserter.contains("internal fun Context.startWebsiteBlocking("),
        )
        assertTrue(
            "컨트롤러는 자기 복사본을 갖지 않아야 한다",
            !controller.contains("fun Context.startWebsiteBlocking("),
        )
        assertTrue(
            "컨트롤러는 공유 프리미티브를 호출해야 한다",
            controller.contains("startWebsiteBlocking("),
        )
    }

    @Test
    fun aFailedRecoveryPutsTheWarningBackInsteadOfLyingBySilence() {
        // 배너의 복구 버튼은 눌리는 즉시 상태를 Inactive 로 낙관적으로 지운다. 세우지 못했는데
        // 그대로 두면 경고만 사라지고 차단은 서지 않아, 사용자는 막히고 있다고 잘못 믿는다.
        // 아무 말도 하지 않는 것보다 나쁘다.
        val consentGuard = asserter.indexOf("VpnService.prepare(context) != null")
        assertTrue("동의 여부를 다시 확인해야 한다", consentGuard >= 0)

        val body = asserter.substring(consentGuard, minOf(consentGuard + 300, asserter.length))
        assertTrue(
            "동의가 없으면 경고가 다시 뜨는 상태로 되돌려야 한다",
            body.contains("WebsiteBlockingStatus.ConsentDenied"),
        )
        assertTrue(
            "세우지 못했다는 사실을 호출자에게 알려야 한다",
            body.contains("return false"),
        )
    }

    @Test
    fun aPastDeadlineIsNotHandedToTheService() {
        // 마감이 과거면 서비스가 서자마자 스스로 멈춘다. 수동 Keep 처럼 마감 없는 잠금은
        // lockTime 이 현재값이라 이 필터에 자연히 걸러진다.
        assertTrue(
            "잠금 화면은 미래 마감만 넘겨야 한다",
            lockViewModel.contains("takeIf { it > nowMillis }"),
        )
    }
}

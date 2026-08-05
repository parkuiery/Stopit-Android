package com.uiery.keep.websiteblocking

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 루틴 웹 차단을 세우는 판정은 한 곳에만 있어야 한다. 알람 수신자가 그 판정을 품고 있으면
 * 알람이 유일한 계기가 되고, 부팅이나 앱 진입에서 창 한가운데를 복구할 방법이 없다.
 */
class RoutineWebsiteBlockingLauncherWiringTest {
    private val launcher =
        File("src/main/java/com/uiery/keep/websiteblocking/AndroidRoutineWebsiteBlockingLauncher.kt").readText()
    private val receiver =
        File("src/main/java/com/uiery/keep/receiver/RoutineAlarmReceiver.kt").readText()

    @Test
    fun theLauncherResolvesTheWindowWithTheSharedPolicy() {
        assertTrue(
            "창 계산은 공유 domain 함수를 써야 부팅·앱 진입과 같은 규칙이 된다",
            launcher.contains("toRoutineWebsiteWindows("),
        )
        assertTrue(
            "세션 판정은 RoutineWebsiteBlockingPolicy 가 소유한다",
            launcher.contains("RoutineWebsiteBlockingPolicy.resolveSession("),
        )
        assertTrue(
            "행동 선택도 정책이 소유해야 계기마다 다르게 굴지 않는다",
            launcher.contains("RoutineWebsiteBlockingApplyPolicy.decide("),
        )
    }

    @Test
    fun theLauncherCarriesEveryActionToItsEffect() {
        assertTrue(
            "차단 시작은 마감 시각과 함께 포그라운드 서비스로 올라가야 한다",
            launcher.contains("ContextCompat.startForegroundService(") &&
                launcher.contains("stopAtEpochMillis = "),
        )
        assertTrue(
            "창이 끝났으면 서 있던 차단을 내려야 한다",
            launcher.contains("KeepDnsVpnService.stopIntent("),
        )
        assertTrue(
            "동의가 없으면 조용히 건너뛰지 말고 상태를 남겨야 한다",
            launcher.contains("WebsiteBlockingStatus.ConsentDenied"),
        )
    }

    @Test
    fun theLauncherKeepsTheDiagnosticLineTheRunbookGrepsFor() {
        // docs/ROUTINE_WEBSITE_BLOCKING_TRIGGER_CONTRACT.md 의 재현 절차가 이 태그를 본다.
        assertTrue(launcher.contains("KeepRoutineWeb"))
        assertTrue(launcher.contains("routine_web total="))
    }

    @Test
    fun theAlarmReceiverDelegatesInsteadOfOwningTheJudgement() {
        assertTrue(
            "수신자는 launcher 에 위임해야 한다",
            receiver.contains("routineWebsiteBlockingLauncher.apply("),
        )
        assertFalse(
            "판정이 수신자에 남아 있으면 계기가 알람 하나로 굳는다",
            receiver.contains("RoutineWebsiteBlockingPolicy.resolveSession("),
        )
        assertFalse(
            "창 계산도 수신자에 남아 있으면 안 된다",
            receiver.contains("toWebsiteWindows()"),
        )
    }
}

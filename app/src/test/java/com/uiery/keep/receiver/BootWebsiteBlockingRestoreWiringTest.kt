package com.uiery.keep.receiver

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 창 도중 재부팅하면 알람은 다시 예약되지만 웹 차단은 켜지지 않았다. 알람은 창의 시작에만
 * 걸려 있으므로, 이미 지나간 시작을 다시 알려주지 않는다.
 *
 * BOOT_COMPLETED 는 백그라운드 포그라운드-서비스 시작 제한의 면제 대상이지만, 그 면제는
 * 수신자가 방송을 처리하는 동안에만 열려 있다. 그래서 판정은 루틴별 알람 재예약 루프보다
 * 먼저 돌아야 한다.
 */
class BootWebsiteBlockingRestoreWiringTest {
    private val source =
        File("src/main/java/com/uiery/keep/receiver/BootReceiver.kt").readText()

    @Test
    fun bootCanStandTheBlockingAgain() {
        assertTrue(
            "부팅 수신자가 웹 차단 판정을 돌릴 수 있어야 한다",
            source.contains("routineWebsiteBlockingLauncher: RoutineWebsiteBlockingLauncher"),
        )
        assertTrue(
            "복구 경로에서 판정을 실제로 호출해야 한다",
            source.contains("routineWebsiteBlockingLauncher.apply("),
        )
    }

    @Test
    fun theJudgementRunsBeforeTheAlarmReschedulingLoop() {
        val apply = source.indexOf("routineWebsiteBlockingLauncher.apply(")
        val loop = source.indexOf("routines.filter { it.isEnabled }.forEach")

        assertTrue("알람 재예약 루프가 있어야 한다", loop >= 0)
        assertTrue("판정 호출이 있어야 한다", apply >= 0)
        assertTrue(
            "면제 창이 열려 있는 동안 시작해야 한다. 루틴이 많으면 재예약 루프가 길어진다",
            apply < loop,
        )
    }
}

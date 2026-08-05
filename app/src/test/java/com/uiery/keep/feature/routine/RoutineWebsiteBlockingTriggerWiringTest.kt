package com.uiery.keep.feature.routine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 창이 이미 진행 중일 때 루틴을 새로 만들거나 켜면, 다음 시작 알람이 올 때까지 웹은 열려
 * 있었다. 루틴을 바꾸는 사람은 그 순간 앱 안에 있으므로 알람을 기다릴 이유가 없다.
 */
class RoutineWebsiteBlockingTriggerWiringTest {
    private val source =
        File("src/main/java/com/uiery/keep/feature/routine/RoutineViewModel.kt").readText()

    @Test
    fun theRoutineScreenCanStandTheBlockingItJustChanged() {
        assertTrue(
            "루틴 화면이 웹 차단 판정을 돌릴 수 있어야 한다",
            source.contains("routineWebsiteBlockingLauncher: RoutineWebsiteBlockingLauncher"),
        )
    }

    @Test
    fun everyRoutineChangeReJudgesTheWindowNotJustTheNextAlarm() {
        // 생성·수정·삭제·토글은 모두 이 목록 흐름으로 다시 흘러나온다. 여기 한 곳에 걸면
        // 변경 경로마다 따로 거는 것보다 새는 곳이 없다.
        val collect = source.indexOf("routineRepository.fetchAll().collect")
        assertTrue("루틴 목록 수집 지점이 있어야 한다", collect >= 0)

        val apply = source.indexOf("routineWebsiteBlockingLauncher.apply(", collect)
        assertTrue(
            "루틴이 바뀔 때마다 웹 차단 판정을 다시 돌려야 한다",
            apply > collect,
        )
        assertTrue(
            "방금 확정된 루틴 목록으로 판정해야 한다",
            source.substring(apply, apply + 80).contains("restoreResult.routines"),
        )
    }
}

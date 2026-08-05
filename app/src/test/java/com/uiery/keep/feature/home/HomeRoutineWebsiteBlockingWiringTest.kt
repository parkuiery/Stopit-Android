package com.uiery.keep.feature.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 알람이 지연·누락되었거나 창 도중 재부팅한 회차는 사용자가 앱을 열었을 때라도 다시 서야
 * 한다. 홈이 수동/타이머 잠금만 보고 판정하면, 루틴 창 한가운데에서 앱을 열어도 아무 일도
 * 일어나지 않는다.
 */
class HomeRoutineWebsiteBlockingWiringTest {
    private val viewModel =
        File("src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt").readText()
    private val screen =
        File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()

    @Test
    fun homeResolvesTheRoutineWindowWithTheSharedPolicy() {
        assertTrue(
            "창 계산은 공유 domain 함수를 써야 알람·부팅과 같은 규칙이 된다",
            viewModel.contains("toRoutineWebsiteWindows("),
        )
        assertTrue(
            "세션 판정도 같은 정책이 소유한다",
            viewModel.contains("RoutineWebsiteBlockingPolicy.resolveSession("),
        )
    }

    @Test
    fun theRuntimeDecisionCarriesTheRoutineWindow() {
        val decision = viewModel.indexOf("WebsiteBlockingRuntimePolicy.decide(")
        assertTrue("런타임 판정 지점이 있어야 한다", decision >= 0)
        assertTrue(
            "루틴 창을 넘기지 않으면 홈은 수동/타이머 잠금만 본다",
            viewModel.substring(decision, decision + 400).contains("routineSession = "),
        )
    }

    @Test
    fun theDecisionWaitsUntilTheRoutineWindowHasBeenRead() {
        // 아직 못 읽은 루틴을 "없음"으로 읽으면, 알람이 세운 차단을 홈이 열리자마자 내린다.
        val loaded = viewModel.indexOf("fun websiteBlockingRuntimeStateLoaded()")
        assertTrue("로딩 게이트가 있어야 한다", loaded >= 0)
        assertTrue(
            "루틴 창도 다 읽은 뒤에 판정해야 한다",
            viewModel.substring(loaded, loaded + 300).contains("routineWebsiteSessionLoaded"),
        )
    }

    @Test
    fun returningToHomeReJudgesTheWindow() {
        // 창은 시간이 지나면서 시작되고 끝난다. 화면이 떠 있는 동안 한 번 읽고 마는 값이 아니다.
        val resume = screen.indexOf("Lifecycle.Event.ON_RESUME")
        assertTrue("ON_RESUME 훅이 있어야 한다", resume >= 0)
        val block = screen.substring(resume, minOf(resume + 700, screen.length))
        assertTrue(
            "앱으로 돌아올 때 루틴 창을 다시 판정해야 한다",
            block.contains("refreshRoutineWebsiteSession()"),
        )
    }
}

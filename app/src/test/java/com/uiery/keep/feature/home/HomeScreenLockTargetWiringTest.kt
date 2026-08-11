package com.uiery.keep.feature.home

import com.uiery.keep.domain.lock.LockTargetKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 홈은 앱과 웹사이트를 하나의 잠금 대상으로 다룬다. 대상 판정이 앱 목록만 보면
 * 웹사이트만 고른 잠금이 화면마다 다르게 취급된다.
 */
class HomeScreenLockTargetWiringTest {
    private val source = File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()

    @Test
    fun categorySheetOffersWebsiteTabAndReportsBothTargetKinds() {
        assertTrue(
            "웹사이트 탭 노출은 플레이버 플래그가 정한다. 화면에 상수로 박으면 " +
                "VPN 배선 전에 prod 로 새어 나간다.",
            source.contains("websiteSelectionEnabled = BuildConfig.WEBSITE_BLOCKING_ENABLED"),
        )
        assertTrue(
            "시트는 저장된 도메인을 초기값으로 받아야 다시 열었을 때 선택이 남는다",
            source.contains("storeSelectedWebDomains = uiState.selectedWebDomains"),
        )

        val completeTargets = source.indexOf("onCompleteTargets = {")
        assertTrue("시트 완료는 앱과 웹사이트를 함께 넘겨야 한다", completeTargets >= 0)
        assertTrue(
            "웹사이트를 포함한 완료는 selectLockTargetsComplete 로 저장해야 한다",
            source.indexOf("viewModel.selectLockTargetsComplete(", completeTargets) > completeTargets,
        )
    }

    @Test
    fun lockEntryPointsJudgeTargetsByAppsAndWebsitesTogether() {
        // 웹사이트만 고른 상태에서 앱 목록만 보면 잠금은 시작되는데 잠금 화면으로 넘어가지
        // 않거나, 켜짐 안내가 사라진다.
        assertFalse(
            "잠금 진입 판정에 selectedAppPackage.isEmpty() 가 남아 있다",
            source.contains("uiState.selectedAppPackage.isEmpty()"),
        )
        assertFalse(
            "잠금 진입 판정에 selectedAppPackage.isNotEmpty() 가 남아 있다",
            source.contains("uiState.selectedAppPackage.isNotEmpty()"),
        )
        assertEquals(
            "타이머 잠금과 Keep 토글 두 경로 모두 대상 판정을 공유해야 한다",
            3,
            Regex("""uiState\.hasSelectedLockTargets\(\)""").findAll(source).count(),
        )
    }

    @Test
    fun activeLocksDriveTheWebsiteBlockingVpn() {
        assertTrue(
            "잠금 상태가 바뀌면 웹 차단 VPN 도 따라가야 한다",
            source.contains("WebsiteBlockingVpnController("),
        )
        assertTrue(
            "VPN 판단은 상태에서 나온다",
            source.contains("decision = uiState.websiteBlockingRuntimeDecision()"),
        )
        assertTrue(
            "동의를 거부해도 잠금은 진행하되 웹사이트가 막히지 않는다는 사실은 알려야 한다",
            source.contains("onConsentDenied ="),
        )
    }

    @Test
    fun websiteRecommendationsAreOnlyOfferedWhereTheWebsiteTabExists() {
        assertTrue(
            "추천 다이얼로그는 대기 중인 추천을 그대로 받아야 한다",
            source.contains("recommendations = uiState.pendingWebsiteRecommendations"),
        )
        assertTrue(
            "도메인은 명시적 수락으로만 추가된다",
            source.contains("onConfirm = viewModel::acceptWebsiteLockRecommendations"),
        )
        // 웹 탭이 닫힌 빌드에서 웹사이트 추가를 제안하면 고를 수도 없는 것을 권하게 된다.
        val dialog = source.indexOf("WebsiteLockRecommendationDialog(")
        val flagGuard = source.lastIndexOf("if (BuildConfig.WEBSITE_BLOCKING_ENABLED)", dialog)
        assertTrue("추천 다이얼로그는 플레이버 플래그 안에 있어야 한다", flagGuard in 0 until dialog)
    }

    @Test
    fun homeStatusCopyAsksTheStateWhatIsActuallyBlocked() {
        assertTrue(
            "홈 하단 문구는 잠금 대상 종류를 따라야 한다",
            source.contains("lockTargetKind = uiState.lockTargetKind()"),
        )
    }

    @Test
    fun websiteOnlySelectionCountsAsALockTarget() {
        assertTrue(
            HomeUiState(selectedWebDomains = setOf("example.com")).hasSelectedLockTargets(),
        )
        assertTrue(
            HomeUiState(selectedAppPackage = setOf("com.example.app")).hasSelectedLockTargets(),
        )
        assertFalse(HomeUiState().hasSelectedLockTargets())
    }

    @Test
    fun lockTargetKindDistinguishesWebsiteOnlyLocks() {
        assertEquals(
            LockTargetKind.Websites,
            HomeUiState(selectedWebDomains = setOf("example.com")).lockTargetKind(),
        )
        assertEquals(
            LockTargetKind.AppsAndWebsites,
            HomeUiState(
                selectedAppPackage = setOf("com.example.app"),
                selectedWebDomains = setOf("example.com"),
            ).lockTargetKind(),
        )
        assertEquals(
            LockTargetKind.Apps,
            HomeUiState(selectedAppPackage = setOf("com.example.app")).lockTargetKind(),
        )
    }
}

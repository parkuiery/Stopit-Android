package com.uiery.keep.feature.onboarding

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SEED에서 `bg.disabled`와 `bg.layer-basement`는 light 모드에서 모두 `gray-200`(#F3F4F5)이다.
 * 따라서 화면 캔버스(basement) 위에 컨트롤을 직접 올리면 disabled 상태가 배경과 대비 1.00:1이 되어
 * 보이지 않는다. Onboarding의 하단 액션 영역은 `layerDefault`로 분리해야 한다.
 */
class OnboardingActionSurfaceContractTest {

    private val onboardingRoot = File("src/main/java/com/uiery/keep/feature/onboarding")

    @Test
    fun bottomActionBarOwnsLayerDefaultSurface() {
        val source = File(onboardingRoot, "OnboardingActionStack.kt").readText()
        val sharedBar = File(
            "src/main/java/com/uiery/keep/ui/component/BottomActionBar.kt",
        ).readText()

        assertTrue(
            "하단 액션 바는 layerDefault 표면을 소유해야 한다",
            sharedBar.contains("background.layerDefault"),
        )
        assertTrue(
            "OnboardingActionStack은 하단 액션 바를 통해 렌더링해야 한다",
            source.contains("OnboardingBottomActionBar {"),
        )
    }

    @Test
    fun bottomActionBarOwnsItsOwnPadding() {
        // CTA가 하나뿐인 화면(목표 잠금, 부모 모드)은 바 안에 여백을 만들어 줄 보조 버튼이 없다.
        // 여백을 콘텐츠에 맡기면 그런 화면에서 버튼이 layerDefault 경계에 그대로 붙는다.
        val sharedBar = File(
            "src/main/java/com/uiery/keep/ui/component/BottomActionBar.kt",
        ).readText()

        assertTrue(
            "하단 액션 바는 자기 위아래 여백을 소유해야 한다",
            sharedBar.contains("padding(top = 16.dp, bottom = 24.dp)"),
        )
    }

    @Test
    fun bottomActionBarPaintsBehindTheNavigationBar() {
        // 표면이 제스처 바 위에서 끊기면 흰 띠 아래로 basement 색 띠가 남아 두 덩어리로 읽힌다.
        // background를 navigationBarsPadding보다 먼저 걸어야 표면이 인셋 뒤까지 칠해진다.
        val sharedBar = File(
            "src/main/java/com/uiery/keep/ui/component/BottomActionBar.kt",
        ).readText()

        val background = sharedBar.indexOf(".background(")
        val navigationBars = sharedBar.indexOf(".navigationBarsPadding()")

        assertTrue("하단 액션 바는 내비게이션 바 인셋을 직접 처리해야 한다", navigationBars > 0)
        assertTrue(
            "background가 navigationBarsPadding보다 먼저 와야 표면이 인셋 뒤까지 칠해진다",
            background in 1 until navigationBars,
        )
    }

    @Test
    fun everyActionBarScreenLeavesTheBottomInsetToTheBar() {
        // 컨테이너가 Scaffold padding을 통째로 소비하면 내비게이션 바 자리가 여백으로 잡혀
        // 액션 바 표면이 그 위에서 끊긴다. 하단 인셋만 빼고 넘겨야 한다.
        val appRoot = File("src/main/java/com/uiery/keep")
        val screensWithBar = listOf(
            "feature/onboarding/goal/GoalSelectScreen.kt",
            "feature/onboarding/intro/IntroScreen.kt",
            "feature/onboarding/notification/NotificationSettingScreen.kt",
            "feature/onboarding/permission/PermissionSettingScreen.kt",
            "feature/onboarding/proposal/PromiseProposalScreen.kt",
            "feature/onboarding/result/PromiseResultScreen.kt",
            "feature/onboarding/select/SelectAppScreen.kt",
            "feature/onboarding/usageaccess/UsageAccessScreen.kt",
            "feature/goallock/GoalLockCreationScreen.kt",
            "feature/goallock/GoalLockEditScreen.kt",
            "feature/parentmode/ParentModeSetupScreen.kt",
        )

        val violations = screensWithBar.filterNot { relativePath ->
            File(appRoot, relativePath).readText().contains("withoutBottomInset()")
        }

        assertTrue(
            "하단 인셋을 통째로 소비해 액션 바 표면이 끊기는 화면:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun secondaryActionDelegatesItsColorToKds() {
        // 보조 동작은 primary CTA보다 위계가 낮아야 한다. 색은 KDS variant가 소유하며, 화면이
        // legacy Material 슬롯으로 덮어쓰지 않는다. `colors.onSurfaceVariant`는 legacy light에서
        // gray1000(#1A1C20)이라 본문과 같은 농도가 되고, disabled 색까지 무력화한다.
        val source = File(onboardingRoot, "OnboardingActionStack.kt").readText()

        assertTrue(
            "보조 동작은 KeepTextButtonVariant.Muted를 사용해야 한다",
            source.contains("variant = KeepTextButtonVariant.Muted"),
        )
        assertFalse(
            "보조 동작 label을 legacy Material 슬롯으로 덮어쓰지 않는다",
            source.contains("colors.onSurfaceVariant"),
        )
    }

    @Test
    fun everyOnboardingCtaSitsOnTheActionBar() {
        val screensWithCta = listOf(
            "intro/IntroScreen.kt",
            "notification/NotificationSettingScreen.kt",
            "permission/PermissionSettingScreen.kt",
            "proposal/PromiseProposalScreen.kt",
            "result/PromiseResultScreen.kt",
            "select/SelectAppScreen.kt",
        )

        val violations = screensWithCta.filterNot { relativePath ->
            File(onboardingRoot, relativePath).readText().contains("OnboardingBottomActionBar")
        }

        assertTrue(
            "basement 위에 CTA가 직접 놓인 화면:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun screenCanvasDoesNotOwnHorizontalPaddingForTheWholeColumn() {
        // 하단 액션 바는 화면 폭을 가득 채워야 basement와 구분되는 띠로 읽힌다. 바깥 Column이
        // horizontal padding을 소유하면 액션 바가 안쪽으로 밀려 배경과 다시 섞인다. 따라서 좌우
        // 여백은 스크롤 콘텐츠가 소유하고, 액션 바는 자기 여백을 직접 관리한다.
        val screens = listOf(
            "goal/GoalSelectScreen.kt",
            "intro/IntroScreen.kt",
            "notification/NotificationSettingScreen.kt",
            "permission/PermissionSettingScreen.kt",
            "proposal/PromiseProposalScreen.kt",
            "result/PromiseResultScreen.kt",
            "select/SelectAppScreen.kt",
            "usageaccess/UsageAccessScreen.kt",
        )

        val violations = screens.filter { relativePath ->
            val source = File(onboardingRoot, relativePath).readText()
            val canvasPadding = Regex("""padding\((?:insets|paddingValues)\)\s*\.padding\(horizontal""")
            canvasPadding.containsMatchIn(source)
        }

        assertTrue(
            "바깥 캔버스가 액션 바까지 horizontal padding으로 감싼 화면:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}

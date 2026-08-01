package com.uiery.keep.feature.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenLayoutContractTest {
    @Test
    fun simultaneousCardsAndMainControlsStayInOneScrollableOrderedSurface() {
        val source = File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()

        // 카드는 중재자가 고른 한 장만 노출된다. 조건이 겹쳐도 쌓이지 않아야 한다.
        val arbiter = source.indexOf("decideHomeCard(")
        assertTrue("홈은 카드 중재자를 거쳐야 한다", arbiter >= 0)
        assertTrue(
            "카드는 when(homeCard) 한 곳에서만 분기해야 한다",
            source.contains("when (homeCard) {"),
        )
        assertEquals(
            "카드 렌더링 분기는 하나여야 한다",
            1,
            Regex("""when \(homeCard\) \{""").findAll(source).count(),
        )

        // 카드와 메인 컨트롤은 같은 스크롤 표면 안에 순서대로 놓인다.
        val scroll = source.indexOf("verticalScroll(rememberScrollState())")
        val controls = source.indexOf("HOME_MAIN_CONTROLS_TEST_TAG", scroll)
        val topContent = source.indexOf("topContent()", scroll)

        assertTrue(scroll >= 0)
        assertTrue(topContent in (scroll + 1) until controls)
        assertTrue(source.contains("contentDescription = keepSwitchDescription"))
        assertTrue(source.contains("contentDescription = stringResource(R.string.cd_open_timer)"))
        assertTrue(source.contains("enabled = !uiState.isKeep && !uiState.hasActiveTimedLock"))
    }

    @Test
    fun contentSurfaceScrollsRegardlessOfHowManyCardsAreVisible() {
        // 카드 조합(재개 카드·사용 인사이트)과 글꼴 크기에 따라 위쪽 높이가 크게 달라진다.
        // 분기마다 스크롤 여부가 갈리면 스크롤 없는 쪽에서 콘텐츠가 잘리고 복구할 방법이 없다.
        val source = File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()

        assertEquals(
            "스크롤 표면은 카드 개수와 무관하게 하나여야 한다",
            1,
            Regex("""verticalScroll\(""").findAll(source).count(),
        )
        assertTrue(
            "메인 컨트롤은 고정 높이가 아니라 최소 높이를 가져야 넘칠 때 잘리지 않는다",
            Regex("""\.heightIn\(min = 260\.dp\)""").containsMatchIn(source),
        )
        assertTrue(
            "짧은 콘텐츠에서도 표면이 화면을 채워야 메인 컨트롤이 위로 딸려 올라가지 않는다",
            source.contains(".heightIn(min = viewportHeight)"),
        )
    }

    @Test
    fun bottomCopyAndBannerStayPinnedOutsideTheScrollableArea() {
        // 하단 문구와 배너를 스크롤 Column 안에 두면 자기 콘텐츠 높이만큼만 자리를 잡아
        // 화면 중간으로 떠오른다. 스크롤 영역이 weight로 남은 높이를 가져가고, 하단 묶음은
        // 그 형제로 남아야 바닥에 붙는다.
        val source = File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()

        val container = source.indexOf("BoxWithConstraints(modifier = Modifier.weight(1f))")
        assertTrue(
            "스크롤 표면은 weight(1f)로 남은 높이를 차지해야 한다",
            container >= 0,
        )

        val containerBody = source.indexOf('{', container)
        val containerEnd = matchingBrace(source, containerBody)
        val bottom = source.indexOf("bottomContent()", container)

        assertTrue("스크롤 표면의 본문을 찾지 못했다", containerBody in 0 until containerEnd)
        assertTrue(
            "bottomContent가 스크롤 표면 안에 있다. 스크롤 안에서는 자기 높이만큼만 " +
                "자리를 잡아 화면 중간으로 떠오른다.",
            bottom > containerEnd,
        )
    }

    @Test
    fun toggleStaysPutBetweenOnAndOffStates() {
        // 켜짐/꺼짐 사이에 중앙 토글이 움직이면 같은 자리를 두 번 누르지 못한다.
        // 상태에 따라 크기가 달라지는 요소가 레이아웃 측정에 관여하지 않아야 한다.
        val source = File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()

        val lottie = source.indexOf("LottieAnimation(")
        val decorationBox = source.lastIndexOf("Modifier.matchParentSize()", lottie)
        assertTrue(
            "켜짐에서만 그려지는 장식 애니메이션은 matchParentSize 로 측정에서 빠져야 한다. " +
                "home_prevent 는 정사각형이라 fillMaxWidth 로 두면 높이가 화면 폭만큼 잡힌다.",
            decorationBox >= 0,
        )

        val statusSlot = File(
            "src/main/java/com/uiery/keep/feature/home/component/ContentDescription.kt",
        ).readText()
        assertTrue(
            "하단 상태 문구는 타이머(32sp)와 꺼짐 문구(18sp)가 같은 높이를 쓰도록 자리를 잡아야 한다",
            Regex("""heightIn\(min = \d+\.dp\)""").containsMatchIn(statusSlot),
        )
    }

    /** [open] 위치의 '{' 와 짝이 되는 '}' 의 인덱스를 돌려준다. */
    private fun matchingBrace(source: String, open: Int): Int {
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    @Test
    fun centralToggleImageKeepsTouchActionButIsHiddenFromTalkBack() {
        val source = File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()
        val controlsStart = source.indexOf("val (image, message)")
        val imageStart = source.indexOf("Image(", controlsStart)
        val imageEnd = source.indexOf("painter = painterResource(id = image)", imageStart)
        val imageModifier = source.substring(imageStart, imageEnd)

        val tagged = imageModifier.indexOf(".testTag(HOME_KEEP_TOGGLE_TOUCH_SHORTCUT_TEST_TAG)")
        val cleared = imageModifier.indexOf(".clearAndSetSemantics { }")
        val clickable = imageModifier.indexOf(".clickable")
        assertTrue(tagged >= 0)
        assertTrue(cleared > tagged)
        assertTrue(clickable > cleared)
    }
}

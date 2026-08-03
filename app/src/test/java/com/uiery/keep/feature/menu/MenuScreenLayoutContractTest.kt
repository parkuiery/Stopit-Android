package com.uiery.keep.feature.menu

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 메뉴 항목 수는 고정이 아니다. 광고 개인정보 항목이 동의 상태에 따라 붙고, 시스템 글자 크기
 * 설정이 커지면 행 높이도 자란다. 스크롤 표면이 없으면 넘친 부분에 닿을 방법이 없다.
 */
class MenuScreenLayoutContractTest {

    private val source = File("src/main/java/com/uiery/keep/feature/menu/MenuScreen.kt").readText()

    @Test
    fun menuContentScrollsWhenItGrowsPastTheViewport() {
        assertTrue(
            "메뉴 목록은 스크롤 표면 안에 있어야 한다",
            source.contains("verticalScroll(rememberScrollState())"),
        )
        assertTrue(
            "스크롤 표면이 weight(1f)로 남은 높이를 차지해야 배너가 바닥에 붙는다",
            Regex("""\.weight\(1f\)\s*\n\s*\.verticalScroll\(""").containsMatchIn(source),
        )
    }

    @Test
    fun bannerStaysPinnedOutsideTheScrollableArea() {
        // 배너를 스크롤 안에 넣으면 콘텐츠가 짧을 때 화면 중간으로 떠오른다. 지금 위치를
        // 유지하려면 스크롤 표면의 형제로 남아야 한다.
        val scrollColumn = source.indexOf(".verticalScroll(rememberScrollState())")
        assertTrue("스크롤 표면을 찾지 못했다", scrollColumn >= 0)

        val scrollBody = source.indexOf('{', source.indexOf(')', scrollColumn))
        val scrollEnd = matchingBrace(source, scrollBody)
        val banner = source.indexOf("TrackedBannerAd(", scrollColumn)

        assertTrue("스크롤 표면의 본문을 찾지 못했다", scrollBody in 0 until scrollEnd)
        assertTrue(
            "배너가 스크롤 표면 안에 있다. 스크롤 안에서는 자기 높이만큼만 자리를 잡아 " +
                "콘텐츠가 짧을 때 화면 중간으로 떠오른다.",
            banner > scrollEnd,
        )
    }

    @Test
    fun slackIsNotAbsorbedByASpacer() {
        // Spacer(weight(1f))는 남는 공간만 흡수한다. 콘텐츠가 화면을 넘기면 0으로 줄어든 뒤
        // 그대로 잘리고, 스크롤이 없으니 복구할 방법이 없다.
        assertTrue(
            "남는 높이는 스크롤 표면이 가져가야 한다. Spacer(weight)로는 넘칠 때 잘린다.",
            !Regex("""Spacer\(modifier = Modifier\.weight\(1f\)\)""").containsMatchIn(source),
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
}

package com.uiery.keep.analytics

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 하단 고정 배너는 앱 콘텐츠와 맞닿아 두 영역이 한 덩어리로 읽힌다. 간격은 배너가 소유해서
 * 화면마다 값이 갈리지 않게 한다.
 */
class BannerAdSeparationContractTest {

    private val bannerSource = File(
        "src/main/java/com/uiery/keep/analytics/TrackedBannerAd.kt",
    ).readText()

    @Test
    fun bannerOwnsItsSeparationFromTheContentAbove() {
        assertTrue(
            "배너는 위 콘텐츠와의 간격을 직접 소유해야 한다",
            bannerSource.contains("contentSeparation: Dp = BannerAdContentSeparation"),
        )
        assertTrue(
            "간격 값은 한 곳에서 정의해야 한다",
            Regex("""val BannerAdContentSeparation = \d+\.dp""").containsMatchIn(bannerSource),
        )
    }

    @Test
    fun separationIsAppliedBeforeTheBannerHeight() {
        // padding 을 height 뒤에 걸면 규격 높이 안에서 배너가 눌려 광고 뷰 크기가 어긋난다.
        val padding = bannerSource.indexOf(".padding(top = contentSeparation)")
        val height = bannerSource.indexOf(".height(MetaCompatibleBannerAdSize.height.dp)")

        assertTrue("배너 여백을 찾지 못했다", padding >= 0)
        assertTrue("배너 높이 지정을 찾지 못했다", height >= 0)
        assertTrue(
            "여백은 height 앞에 와야 배너가 규격 높이를 그대로 쓴다",
            padding < height,
        )
    }

    @Test
    fun onlyTheTopPlacedBannerOptsOut() {
        // 위쪽에 놓이는 배너는 위 여백이 화면 끝과의 간격이 되어 배너를 아래로 민다.
        val optOuts = File("src/main/java/com/uiery/keep").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("contentSeparation = 0.dp") }
            .map { it.name }
            .toList()

        assertTrue(
            "위 여백을 끄는 배너는 상단 배치인 BlockScreen 하나여야 한다. 지금: $optOuts",
            optOuts == listOf("BlockScreen.kt"),
        )
    }

    @Test
    fun homeDoesNotStackItsOwnSeparationOnTop() {
        // 홈은 원래 바로 위 문구가 bottom padding 으로 간격을 만들었다. 배너가 소유하게 된
        // 뒤에도 남아 있으면 간격이 두 번 들어간다.
        val home = File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()
        val bottomContent = home.indexOf("val bottomContent")
        val banner = home.indexOf("TrackedBannerAd(", bottomContent)
        val block = home.substring(bottomContent, banner)

        assertFalse(
            "배너 바로 위 요소가 자기 bottom padding 으로 간격을 또 만들고 있다",
            Regex("""\.padding\(bottom = \d+\.dp\)""").containsMatchIn(block),
        )
    }
}

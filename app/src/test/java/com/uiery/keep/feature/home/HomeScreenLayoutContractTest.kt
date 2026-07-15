package com.uiery.keep.feature.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenLayoutContractTest {
    @Test
    fun simultaneousCardsAndMainControlsStayInOneScrollableOrderedSurface() {
        val source = File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()

        val scroll = source.indexOf("verticalScroll(rememberScrollState())")
        val category = source.indexOf("CategoryButton(", scroll)
        val firstLock = source.indexOf("FirstLockActivationCta(", category)
        val resume = source.indexOf("FirstPromiseResumeCard(", firstLock)
        val insight = source.indexOf("UsageInsightCard(", resume)
        val controls = source.indexOf("HOME_MAIN_CONTROLS_TEST_TAG", insight)

        assertTrue(scroll >= 0)
        assertTrue(category in (scroll + 1) until firstLock)
        assertTrue(firstLock < resume)
        assertTrue(resume < insight)
        assertTrue(insight < controls)
        assertTrue(source.contains("modifier = if (hasFirstPromiseResumeCard)"))
        assertTrue(source.contains("mainControls(Modifier.fillMaxSize())"))
        assertTrue(source.contains("contentAlignment = Alignment.BottomCenter"))
        assertTrue(source.contains("contentDescription = keepSwitchDescription"))
        assertTrue(source.contains("contentDescription = stringResource(R.string.cd_open_timer)"))
    }

    @Test
    fun centralToggleImageKeepsTouchActionButIsHiddenFromTalkBack() {
        val source = File("src/main/java/com/uiery/keep/feature/home/HomeScreen.kt").readText()
        val controlsStart = source.indexOf("val (image, message)")
        val imageStart = source.indexOf("Image(", controlsStart)
        val imageEnd = source.indexOf("painter = painterResource(id = image)", imageStart)
        val imageModifier = source.substring(imageStart, imageEnd)

        val clickable = imageModifier.indexOf(".clickable")
        val cleared = imageModifier.indexOf(".clearAndSetSemantics { }")
        assertTrue(clickable >= 0)
        assertTrue(cleared > clickable)
    }
}

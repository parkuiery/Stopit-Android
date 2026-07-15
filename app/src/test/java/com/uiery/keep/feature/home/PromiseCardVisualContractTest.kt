package com.uiery.keep.feature.home

import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromiseCardVisualContractTest {
    @Test
    fun promiseCardsUseEstablishedHighContrastCardTokens() {
        val result = source("feature/onboarding/result/PromiseResultScreen.kt")
        val resume = source("feature/home/component/FirstPromiseResumeCard.kt")

        listOf(result, resume).forEach { cardSource ->
            assertTrue(cardSource.contains("containerColor = KeepTheme.colors.onSecondary"))
            assertFalse(cardSource.contains("containerColor = KeepTheme.colors.surface)"))
            assertTrue(cardSource.contains("color = KeepTheme.colors.onSurfaceVariant"))
            assertTrue(cardSource.contains("color = KeepTheme.colors.surfaceVariant"))
        }
    }

    @Test
    fun establishedCardTextTokensMeetNormalTextContrastInLightAndDark() {
        val lightCard = 0xF2F4F6
        val lightBody = 0x333D4B
        val lightTitle = 0x191F28
        val darkCard = 0x2C2C35
        val darkBody = 0xE4E4E5
        val darkTitle = 0xFFFFFF

        assertTrue(contrast(lightCard, lightBody) >= 4.5)
        assertTrue(contrast(lightCard, lightTitle) >= 4.5)
        assertTrue(contrast(darkCard, darkBody) >= 4.5)
        assertTrue(contrast(darkCard, darkTitle) >= 4.5)
    }

    @Test
    fun resumeCardExposesTruthfulBusyAndPoliteRetrySemantics() {
        val resume = source("feature/home/component/FirstPromiseResumeCard.kt")

        assertTrue(resume.contains("enabled = !state.isBusy"))
        assertTrue(resume.contains("R.string.first_promise_resume_waiting"))
        assertTrue(resume.contains("liveRegion = LiveRegionMode.Polite"))
    }

    @Test
    fun resultErrorFeedbackUsesAaForegroundAndKeepsPoliteAnnouncement() {
        val result = source("feature/onboarding/result/PromiseResultScreen.kt")
        val feedbackStart = result.indexOf("if (state.practiceFailed || state.activationFailed)")
        val feedbackEnd = result.indexOf("ResultActions(state, viewModel)", feedbackStart)
        val feedback = result.substring(feedbackStart, feedbackEnd)

        assertTrue(feedback.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(feedback.contains("color = KeepTheme.colors.onSurfaceVariant"))
        assertFalse(feedback.contains("color = KeepTheme.colors.error"))
        assertTrue(contrast(0xFFFFFF, 0x191F28) >= 4.5)
        assertTrue(contrast(0x17171C, 0xFFFFFF) >= 4.5)
    }
}

private fun source(relativePath: String): String =
    File("src/main/java/com/uiery/keep/$relativePath").readText()

private fun contrast(first: Int, second: Int): Double {
    val firstLuminance = luminance(first)
    val secondLuminance = luminance(second)
    return (max(firstLuminance, secondLuminance) + 0.05) /
        (min(firstLuminance, secondLuminance) + 0.05)
}

private fun luminance(rgb: Int): Double {
    fun channel(shift: Int): Double {
        val value = ((rgb shr shift) and 0xFF) / 255.0
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

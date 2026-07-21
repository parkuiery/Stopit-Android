package com.uiery.keep.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uiery.kds.theme.KeepTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingActionStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun secondaryActionComesBeforePrimaryActionAndBothKeepTheirCallbacks() {
        val clicks = mutableListOf<String>()

        composeRule.setContent {
            KeepTheme {
                OnboardingActionStack(
                    primaryText = "Primary",
                    secondaryText = "Secondary",
                    onPrimaryClick = { clicks += "primary" },
                    onSecondaryClick = { clicks += "secondary" },
                )
            }
        }

        val secondaryBounds = composeRule.onNodeWithText("Secondary").fetchSemanticsNode().boundsInRoot
        val primaryBounds = composeRule.onNodeWithText("Primary").fetchSemanticsNode().boundsInRoot
        assertTrue(secondaryBounds.center.y < primaryBounds.center.y)

        composeRule.onNodeWithText("Secondary").performClick()
        composeRule.onNodeWithText("Primary").performClick()
        assertEquals(listOf("secondary", "primary"), clicks)
    }
}

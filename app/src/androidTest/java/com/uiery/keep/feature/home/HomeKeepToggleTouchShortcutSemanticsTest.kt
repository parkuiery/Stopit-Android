package com.uiery.keep.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeKeepToggleTouchShortcutSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clearedShortcutHasNoSemanticClickButStillHandlesPointerInput() {
        var clickCount = 0
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .testTag(TEST_TAG)
                    .clearAndSetSemantics { }
                    .clickable { clickCount++ },
            )
        }

        val shortcut = composeRule.onNodeWithTag(TEST_TAG, useUnmergedTree = true)
        assertFalse(shortcut.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))

        shortcut.performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }
}

private const val TEST_TAG = "home_keep_toggle_touch_shortcut_test"

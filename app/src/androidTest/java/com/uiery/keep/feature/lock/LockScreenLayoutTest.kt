package com.uiery.keep.feature.lock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LockScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bannerUsesFullWidthBottomSlotBelowProtectedContent() {
        composeRule.setContent {
            KeepTheme {
                LockScreenLayout(
                    modifier = Modifier.size(width = 360.dp, height = 800.dp).testTag(ROOT_TAG),
                    content = {
                        Box(Modifier.fillMaxSize().testTag(CONTENT_TAG))
                    },
                    banner = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag(BANNER_TAG),
                        )
                    },
                )
            }
        }

        val root = composeRule.onNodeWithTag(ROOT_TAG).fetchSemanticsNode().boundsInRoot
        val content = composeRule.onNodeWithTag(CONTENT_TAG).fetchSemanticsNode().boundsInRoot
        val banner = composeRule.onNodeWithTag(BANNER_TAG).fetchSemanticsNode().boundsInRoot

        assertEquals(root.left, banner.left, 0.5f)
        assertEquals(root.right, banner.right, 0.5f)
        assertTrue(content.bottom <= banner.top)
        assertTrue(banner.bottom <= root.bottom)
    }

    private companion object {
        const val ROOT_TAG = "lock_screen_layout"
        const val CONTENT_TAG = "lock_screen_protected_content"
        const val BANNER_TAG = "lock_screen_banner"
    }
}

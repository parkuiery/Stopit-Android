package com.uiery.keep.ui.component

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryBottomSheetContentIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionStateUpdatesVisibleChecksAndCompletionPayloadAfterFiltering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completedSelections = mutableListOf<Set<String>>()
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = "com.example.beta", appName = "Beta Notes"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    storeSelectApps = setOf("com.example.beta"),
                    onComplete = { completedSelections += it },
                )
            }
        }

        composeRule.onNodeWithTag("category_app_checkbox_com.example.beta").assertIsOn()
        composeRule.onNodeWithTag("category_app_checkbox_com.example.alpha").assertIsOff()

        composeRule.onNodeWithText(context.getString(R.string.search)).performTextInput("Alpha")
        composeRule.onNodeWithText("Alpha Focus").performClick()
        composeRule.onNodeWithTag("category_app_checkbox_com.example.alpha").assertIsOn()

        composeRule.onNodeWithTag("category_selection_complete").performClick()

        assertEquals(listOf(setOf("com.example.alpha", "com.example.beta")), completedSelections)
    }

    @Test
    fun allAppsToggleClearsAndRestoresVisibleCheckboxState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completedSelections = mutableListOf<Set<String>>()
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = "com.example.beta", appName = "Beta Notes"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    storeSelectApps = emptySet(),
                    onComplete = { completedSelections += it },
                )
            }
        }

        composeRule.onNodeWithTag("category_select_all_checkbox").assertIsOff()
        composeRule.onNodeWithTag("category_select_all_row").performClick()
        composeRule.onNodeWithTag("category_select_all_checkbox").assertIsOn()
        composeRule.onNodeWithTag("category_app_checkbox_com.example.alpha").assertIsOn()
        composeRule.onNodeWithTag("category_app_checkbox_com.example.beta").assertIsOn()

        composeRule.onNodeWithTag("category_select_all_row").performClick()
        composeRule.onNodeWithTag("category_select_all_checkbox").assertIsOff()
        composeRule.onNodeWithTag("category_app_checkbox_com.example.alpha").assertIsOff()
        composeRule.onNodeWithTag("category_app_checkbox_com.example.beta").assertIsOff()

        composeRule.onNodeWithTag("category_selection_complete").performClick()

        assertEquals(listOf(emptySet<String>()), completedSelections)
    }

    private fun testApp(
        packageName: String,
        appName: String,
    ): AppInfo = AppInfo(
        isChecked = false,
        packageName = packageName,
        appName = appName,
        appIcon = BitmapDrawable(
            Resources.getSystem(),
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
        ),
    )
}

package com.uiery.keep.qa

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.uiery.keep.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release-candidate UI smoke test based on Android's testing-setup skill:
 * keep end-to-end tests small, run them on a device/emulator, and use
 * Compose semantics first with UIAutomator for device/runtime visibility.
 */
@RunWith(AndroidJUnit4::class)
class StopitReleaseSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesIntoComposeNavigationHost() {
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("stopit_app_nav_host", useUnmergedTree = true)
            .assertExists()

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val packageVisible = device.wait(
            Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)),
            LAUNCH_TIMEOUT_MS,
        )

        assertTrue(
            "Expected StopIt package to be visible after release UI smoke launch",
            packageVisible,
        )
    }

    private companion object {
        const val TARGET_PACKAGE = "com.uiery.keep"
        const val LAUNCH_TIMEOUT_MS = 5_000L
    }
}

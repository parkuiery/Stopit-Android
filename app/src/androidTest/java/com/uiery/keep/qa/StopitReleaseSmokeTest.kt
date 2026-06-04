package com.uiery.keep.qa

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        waitUntil("Expected StopIt package $targetPackage to be visible after release UI smoke launch") {
            isTargetPackageForeground(device, targetPackage)
        }
    }

    private fun isTargetPackageForeground(device: UiDevice, targetPackage: String): Boolean {
        if (device.executeShellCommand(
                "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'",
            ).contains("$targetPackage/")) {
            return true
        }

        return device.executeShellCommand(
            "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'",
        ).contains(targetPackage)
    }

    private fun waitUntil(message: String, timeoutMs: Long = LAUNCH_TIMEOUT_MS, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(100)
        }
        assertTrue(message, condition())
    }

    private companion object {
        const val LAUNCH_TIMEOUT_MS = 5_000L
    }
}

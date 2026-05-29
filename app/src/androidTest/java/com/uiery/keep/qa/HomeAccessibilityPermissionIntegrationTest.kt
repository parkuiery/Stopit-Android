package com.uiery.keep.qa

import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.uiery.keep.MainActivity
import com.uiery.keep.R
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.dataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAccessibilityPermissionIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device by lazy { UiDevice.getInstance(instrumentation) }

    private var originalAccessibilityEnabled: String = ""
    private var originalEnabledServices: String = ""

    @Before
    fun setUp() {
        runBlocking {
            originalAccessibilityEnabled = shell("settings get secure accessibility_enabled").trim()
            originalEnabledServices = shell("settings get secure enabled_accessibility_services").trim()
            configureReturningUserHomeState()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            restoreAccessibilitySettings()
            clearHomeState()
            device.pressHome()
        }
    }

    @Test
    fun fakePackageSubstringStillShowsAccessibilityPermissionDialogOnHome() {
        setAccessibilitySettings(
            accessibilityEnabled = "1",
            enabledServices = "com.uiery.keep.fake/com.fake.Service:com.other/.Helper",
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForStopItForeground()

            waitUntil("Expected home permission dialog when only a package substring matches") {
                device.hasObject(By.text(permissionDialogTitle))
            }
        }
    }


    private suspend fun configureReturningUserHomeState() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.IS_NEW] = false
            preferences.remove(PreferencesKey.LOCK_TIME)
        }
    }

    private suspend fun clearHomeState() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.IS_NEW)
            preferences.remove(PreferencesKey.LOCK_TIME)
        }
    }

    private fun restoreAccessibilitySettings() {
        setAccessibilitySettings(
            accessibilityEnabled = originalAccessibilityEnabled.ifBlank { "0" },
            enabledServices = originalEnabledServices,
        )
    }


    private fun setAccessibilitySettings(
        accessibilityEnabled: String,
        enabledServices: String,
    ) {
        shell("settings put secure accessibility_enabled $accessibilityEnabled")
        if (enabledServices.isBlank()) {
            shell("settings put secure enabled_accessibility_services ''")
        } else {
            shell("settings put secure enabled_accessibility_services '$enabledServices'")
        }
    }

    private fun waitForStopItForeground() {
        waitUntil("Expected StopIt package to be foreground") {
            isTargetPackageForeground()
        }
    }

    private fun isTargetPackageForeground(): Boolean {
        if (shell("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'")
                .contains("$targetPackage/")) {
            return true
        }

        return shell("dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'")
            .contains(targetPackage)
    }

    private fun shell(command: String): String = device.executeShellCommand(command)

    private fun waitUntil(message: String, timeoutMs: Long = 8_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue(message, condition())
    }

    private val permissionDialogTitle: String
        get() = context.getString(R.string.permission_dialog_title)

    private companion object {
        const val targetPackage = "com.uiery.keep"
        const val keepServiceComponent = "com.uiery.keep/com.uiery.keep.service.KeepAccessibilityService"
    }
}

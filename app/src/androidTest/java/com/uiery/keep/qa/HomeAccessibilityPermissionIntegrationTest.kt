package com.uiery.keep.qa

import android.app.UiAutomation
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.uiery.keep.MainActivity
import com.uiery.keep.R
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.dataStore
import com.uiery.keep.util.hasAccessibilityPermission
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.fail

@RunWith(AndroidJUnit4::class)
class HomeAccessibilityPermissionIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device by lazy { UiDevice.getInstance(instrumentation) }

    private var originalAccessibilityEnabled: String = ""
    private var originalEnabledServices: String = ""
    private var originalUiAutomationFlags = 0

    @Before
    fun setUp() {
        runBlocking {
            originalUiAutomationFlags = Configurator.getInstance().uiAutomationFlags
            Configurator.getInstance().setUiAutomationFlags(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
            )
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
            Configurator.getInstance().setUiAutomationFlags(originalUiAutomationFlags)
        }
    }

    @Test
    fun fakePackageSubstringStillShowsAccessibilityPermissionDialogOnHome() {
        setInvalidAccessibilitySettings(
            accessibilityEnabled = "1",
            enabledServices = "com.uiery.keep.fake/com.fake.Service:com.other/.Helper",
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForStopItForeground()
            it.onActivity { activity ->
                assertFalse(
                    "hasAccessibilityPermission should reject fake package substring services",
                    hasAccessibilityPermission(activity),
                )
            }
            it.moveToState(Lifecycle.State.STARTED)
            it.moveToState(Lifecycle.State.RESUMED)
            waitForStopItForeground()

            waitForPermissionDialog(
                "Expected home permission dialog when only a package substring matches",
            )
        }
    }

    @Test
    fun returningFromAccessibilitySettingsResyncsHomePermissionDialogOnResume() {
        setAccessibilitySettings(
            accessibilityEnabled = "1",
            enabledServices = keepServiceComponent,
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForStopItForeground()
            assertFalse(
                "Permission dialog should stay hidden while accessibility is enabled for KeepAccessibilityService",
                device.hasObject(By.text(permissionDialogTitle)),
            )

            disableAccessibilityServiceFromSettings()
            waitForStopItForeground()
            it.onActivity { activity ->
                assertFalse(
                    "hasAccessibilityPermission should be false after disabling the service from Settings",
                    hasAccessibilityPermission(activity),
                )
            }
            waitForPermissionDialog(
                "Expected home permission dialog after accessibility is disabled and the app resumes",
            )
        }
    }

    @Test
    fun returningFromAccessibilitySettingsClearsHomePermissionDialogAfterReEnablingService() {
        setAccessibilitySettings(
            accessibilityEnabled = "0",
            enabledServices = "",
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForStopItForeground()
            waitForPermissionDialog(
                "Expected home permission dialog before enabling accessibility from Settings",
            )

            enableAccessibilityServiceFromSettings()
            waitForStopItForeground()
            it.onActivity { activity ->
                assertTrue(
                    "hasAccessibilityPermission should be true after enabling KeepAccessibilityService from Settings",
                    hasAccessibilityPermission(activity),
                )
            }
            waitUntil("Expected home permission dialog to disappear after accessibility is re-enabled and the app resumes") {
                !device.hasObject(By.text(permissionDialogTitle))
            }
        }
    }

    private suspend fun configureReturningUserHomeState() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.IS_NEW] = false
            preferences[PreferencesKey.IS_KEEP] = false
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.START_TIME)
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
        }
    }

    private suspend fun clearHomeState() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.IS_NEW)
            preferences.remove(PreferencesKey.IS_KEEP)
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.START_TIME)
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
        }
    }

    private fun restoreAccessibilitySettings() {
        setAccessibilitySettings(
            accessibilityEnabled = originalAccessibilityEnabled.ifBlank { "0" },
            enabledServices = originalEnabledServices,
        )
    }

    private fun disableAccessibilityServiceFromSettings() {
        openAccessibilityServiceDetails()
        waitUntil("Could not find Accessibility main switch for StopIt service") {
            device.hasObject(By.res(settingsPackage, mainSwitchBarId))
        }
        device.findObject(By.res(settingsPackage, mainSwitchBarId))?.click()
            ?: fail("Could not find Accessibility main switch for StopIt service")

        waitUntil("Could not find disable confirmation button for Accessibility permission dialog") {
            device.hasObject(By.res(androidButtonId)) || device.hasObject(By.res(disableButtonId))
        }
        device.findObject(By.res(disableButtonId))?.click()
            ?: device.findObject(By.res(androidButtonId))?.click()
            ?: fail("Could not find disable confirmation button for Accessibility permission dialog")

        waitUntil("Expected KeepAccessibilityService to become disabled in secure settings") {
            shell("settings get secure accessibility_enabled").trim() == "0" ||
                !shell("settings get secure enabled_accessibility_services").contains(keepServiceComponent)
        }
        device.pressHome()
        launchStopIt()
    }

    private fun enableAccessibilityServiceFromSettings() {
        openAccessibilityServiceDetails()
        setAccessibilitySettings(
            accessibilityEnabled = "1",
            enabledServices = keepServiceComponent,
        )
        waitUntil("Expected KeepAccessibilityService to become enabled in secure settings") {
            shell("settings get secure accessibility_enabled").trim() == "1" &&
                shell("settings get secure enabled_accessibility_services").contains(keepServiceComponent)
        }
        device.pressHome()
        launchStopIt()
    }

    private fun openAccessibilityServiceDetails() {
        repeat(3) { attempt ->
            if (openAccessibilityServiceDetailsViaIntent()) return
            if (openAccessibilityServiceDetailsFromList()) return
            if (attempt < 2) {
                device.pressBack()
                device.waitForIdle()
                Thread.sleep(500)
            }
        }

        fail("StopIt Accessibility detail screen should open")
    }

    private fun openAccessibilityServiceDetailsViaIntent(): Boolean {
        shell("am force-stop $settingsPackage")
        shell(
            "am start -W -a android.settings.ACCESSIBILITY_DETAILS_SETTINGS " +
                "--es android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME $keepServiceComponent",
        )
        device.waitForIdle()
        return device.hasObject(By.res(settingsPackage, mainSwitchBarId))
    }

    private fun openAccessibilityServiceDetailsFromList(): Boolean {
        shell("am force-stop $settingsPackage")
        shell("am start -W -a android.settings.ACCESSIBILITY_SETTINGS")
        device.waitForIdle()

        val scrollable = UiScrollable(UiSelector().scrollable(true)).apply {
            setAsVerticalList()
        }
        if (!device.hasObject(By.text(appName))) {
            scrollable.scrollTextIntoView(appName)
        }

        val serviceEntry = device.findObject(UiSelector().text(appName))
        if (!serviceEntry.exists()) {
            return false
        }

        serviceEntry.click()
        device.waitForIdle()
        return device.hasObject(By.res(settingsPackage, mainSwitchBarId))
    }

    private fun launchStopIt() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
        assertTrue("Expected a launch intent for $targetPackage", launchIntent != null)
        context.startActivity(
            launchIntent!!.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        device.waitForIdle()
    }

    private fun setAccessibilitySettings(
        accessibilityEnabled: String,
        enabledServices: String,
    ) {
        val expectedEnabledServices = normalizeSecureSetting(enabledServices)

        shell("settings put secure accessibility_enabled $accessibilityEnabled")
        if (expectedEnabledServices.isBlank()) {
            shell("settings delete secure enabled_accessibility_services")
        } else {
            shell("settings put secure enabled_accessibility_services $expectedEnabledServices")
        }

        waitUntil(
            message = "Expected secure accessibility settings to settle to accessibility_enabled=$accessibilityEnabled " +
                "and enabled_accessibility_services=$expectedEnabledServices; actual=${accessibilitySettingsSnapshot()}",
        ) {
            val actualAccessibilityEnabled = shell("settings get secure accessibility_enabled").trim()
            val actualEnabledServices = normalizeSecureSetting(
                shell("settings get secure enabled_accessibility_services"),
            )
            actualAccessibilityEnabled == accessibilityEnabled &&
                actualEnabledServices == expectedEnabledServices
        }
    }

    private fun setInvalidAccessibilitySettings(
        accessibilityEnabled: String,
        enabledServices: String,
    ) {
        shell("settings put secure accessibility_enabled $accessibilityEnabled")
        shell("settings put secure enabled_accessibility_services $enabledServices")

        waitUntil(
            message = "Expected invalid accessibility service setting to settle without granting " +
                "KeepAccessibilityService; actual=${accessibilitySettingsSnapshot()}",
        ) {
            val actualAccessibilityEnabled = shell("settings get secure accessibility_enabled").trim()
            val actualEnabledServices = normalizeSecureSetting(
                shell("settings get secure enabled_accessibility_services"),
            )
            actualAccessibilityEnabled == accessibilityEnabled &&
                !actualEnabledServices.split(':').contains(keepServiceComponent)
        }
    }

    private fun accessibilitySettingsSnapshot(): String =
        "accessibility_enabled=${shell("settings get secure accessibility_enabled").trim()}, " +
            "enabled_accessibility_services=${normalizeSecureSetting(shell("settings get secure enabled_accessibility_services"))}"

    private fun normalizeSecureSetting(value: String): String =
        value.trim().takeUnless { it == "null" }.orEmpty()

    private fun waitForStopItForeground() {
        waitForPackageForeground(targetPackage)
    }

    private fun waitForPermissionDialog(message: String) {
        waitUntil(
            message = "$message; ${accessibilitySettingsSnapshot()}; foreground=${isTargetPackageForeground()}",
            timeoutMs = HOME_PERMISSION_DIALOG_TIMEOUT_MS,
        ) {
            device.hasObject(By.text(permissionDialogTitle))
        }
    }

    private fun waitForPackageForeground(packageName: String) {
        waitUntil("Expected $packageName to be foreground") {
            isPackageForeground(packageName)
        }
    }

    private fun isTargetPackageForeground(): Boolean = isPackageForeground(targetPackage)

    private fun isPackageForeground(packageName: String): Boolean {
        if (shell("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'")
                .contains("$packageName/")) {
            return true
        }

        return shell("dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'")
            .contains(packageName)
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

    private val appName: String
        get() = context.packageManager.getApplicationLabel(context.applicationInfo).toString()

    private companion object {
        const val targetPackage = "com.uiery.keep"
        const val settingsPackage = "com.android.settings"
        const val keepServiceComponent = "com.uiery.keep/com.uiery.keep.service.KeepAccessibilityService"
        const val mainSwitchBarId = "main_switch_bar"
        const val androidButtonId = "android:id/button1"
        const val disableButtonId = "android:id/accessibility_permission_disable_stop_button"
        const val HOME_PERMISSION_DIALOG_TIMEOUT_MS = 15_000L
    }
}

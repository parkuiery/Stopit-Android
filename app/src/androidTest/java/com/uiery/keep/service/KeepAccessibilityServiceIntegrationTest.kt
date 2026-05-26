package com.uiery.keep.service

import android.content.Intent
import android.app.UiAutomation
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.dataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeepAccessibilityServiceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device by lazy { UiDevice.getInstance(instrumentation) }

    private var accessibilityServiceInitiallyEnabled = false
    private var originalUiAutomationFlags = 0

    @Before
    fun setUp() {
        runBlocking {
            originalUiAutomationFlags = Configurator.getInstance().uiAutomationFlags
            Configurator.getInstance().setUiAutomationFlags(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
            )
            clearAccessibilityBlockState()
            primeAppProcess()
            device.pressHome()
            accessibilityServiceInitiallyEnabled = isAccessibilityServiceEnabled()
            resetDebugStateRetainingConnectionFlag()
            enableAccessibilityServiceIfNeeded()
            waitUntil("KeepAccessibilityService should be enabled for the runtime test") {
                isAccessibilityServiceEnabled()
            }
            primeAppProcess()
            waitForServiceStatePropagation()
            device.pressHome()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            clearAccessibilityBlockState()
            device.pressHome()
            Configurator.getInstance().setUiAutomationFlags(originalUiAutomationFlags)
        }
    }

    @Test
    fun selectedAppWithManualKeep_launchesBlockActivity() = runBlocking {
        val blockedPackage = resolveLaunchablePackages().first()
        configureManualKeepBlock(blockedPackage)
        waitForServiceStatePropagation()
        waitForServiceToObserveSelectedPackage(blockedPackage)

        launchPackage(blockedPackage)
        waitForWindowEvent(blockedPackage)

        val blockVisible = device.wait(
            Until.hasObject(By.pkg(APP_PACKAGE).depth(0)),
            PACKAGE_VISIBILITY_TIMEOUT_MS,
        )
        assertTrue("Expected BlockActivity package to take foreground when $blockedPackage launches", blockVisible)
    }

    @Test
    fun emergencyUnlockActive_keepsSelectedAppForegroundInsteadOfLaunchingBlockActivity() = runBlocking {
        val bypassPackage = resolveLaunchablePackages().last()
        configureManualKeepBlock(bypassPackage)
        configureEmergencyUnlock(bypassPackage)
        waitForServiceStatePropagation()
        waitForServiceToObserveSelectedPackage(bypassPackage)

        launchPackage(bypassPackage)
        waitForWindowEvent(bypassPackage)

        val targetVisible = device.wait(
            Until.hasObject(By.pkg(bypassPackage).depth(0)),
            PACKAGE_VISIBILITY_TIMEOUT_MS,
        )
        val blockVisible = device.hasObject(By.pkg(APP_PACKAGE).depth(0))
        assertTrue("Expected $bypassPackage to stay foreground while emergency unlock is active", targetVisible)
        assertFalse("Did not expect BlockActivity to launch while emergency unlock is active", blockVisible)
    }

    private suspend fun configureManualKeepBlock(packageName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.SELECTED_APP_PACKAGES] = setOf(packageName)
            preferences[PreferencesKey.IS_KEEP] = true
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
    }

    private suspend fun configureEmergencyUnlock(packageName: String) {
        val expireTimeMillis = System.currentTimeMillis() + EMERGENCY_UNLOCK_WINDOW_MS
        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS] = setOf(packageName)
            preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] = expireTimeMillis
        }
        EmergencyUnlockState.current = EmergencyUnlockData(
            unlockedApps = setOf(packageName),
            expireTimeMillis = expireTimeMillis,
        )
    }

    private suspend fun clearAccessibilityBlockState() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
            preferences.remove(PreferencesKey.IS_KEEP)
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
    }

    private fun resolveLaunchablePackages(): List<String> {
        val packages = LAUNCHABLE_PACKAGE_CANDIDATES.filter { candidate ->
            context.packageManager.getLaunchIntentForPackage(candidate) != null
        }
        assertTrue(
            "Expected at least two launchable system packages for Accessibility runtime tests, found $packages",
            packages.size >= 2,
        )
        return packages.take(2)
    }

    private fun launchPackage(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        assertNotNull("Expected a launch intent for $packageName", launchIntent)
        device.pressHome()
        context.startActivity(
            launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    private fun primeAppProcess() {
        launchPackage(APP_PACKAGE)
        waitUntil("StopIt app should leave the stopped state before Accessibility service setup") {
            !shell("dumpsys package $APP_PACKAGE").contains("stopped=true")
        }
    }

    private fun enableAccessibilityServiceIfNeeded() {
        if (isAccessibilityServiceEnabled()) return

        openAccessibilityServiceDetails()
        waitUntil("Could not find Accessibility main switch for StopIt service", UI_TIMEOUT_MS) {
            device.hasObject(By.res(SETTINGS_PACKAGE, MAIN_SWITCH_BAR_ID))
        }
        device.findObject(By.res(SETTINGS_PACKAGE, MAIN_SWITCH_BAR_ID))?.click()
            ?: fail("Could not find Accessibility main switch for StopIt service")

        waitUntil("Could not find Allow button for Accessibility permission dialog", UI_TIMEOUT_MS) {
            device.hasObject(By.res(ALLOW_BUTTON_ID))
        }
        device.findObject(By.res(ALLOW_BUTTON_ID))?.click()
            ?: fail("Could not find Allow button for Accessibility permission dialog")
    }

    private fun openAccessibilityServiceDetails() {
        shell("am force-stop $SETTINGS_PACKAGE")
        shell("am start -W -a android.settings.ACCESSIBILITY_SETTINGS")
        device.waitForIdle()
        val scrollable = UiScrollable(UiSelector().scrollable(true)).apply {
            setAsVerticalList()
        }
        if (!device.hasObject(By.text(APP_NAME))) {
            scrollable.scrollTextIntoView(APP_NAME)
        }
        val serviceEntry = device.findObject(UiSelector().text(APP_NAME))
        if (!serviceEntry.exists()) {
            fail("Could not find $APP_NAME entry in Accessibility settings")
        }
        serviceEntry.click()
        waitUntil("StopIt Accessibility detail screen should open") {
            device.hasObject(By.res(SETTINGS_PACKAGE, MAIN_SWITCH_BAR_ID))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean =
        shell("dumpsys accessibility").contains("Enabled services:{{$SERVICE_COMPONENT}}")

    private fun waitForServiceStatePropagation() {
        val deadline = System.currentTimeMillis() + SERVICE_PROPAGATION_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (KeepAccessibilityServiceDebugState.read(context).isServiceConnected) {
                return
            }
            Thread.sleep(100)
        }

        val snapshot = KeepAccessibilityServiceDebugState.read(context)
        val accessibilityEnabled = shell("settings get secure accessibility_enabled").trim()
        val enabledServices = shell("settings get secure enabled_accessibility_services").trim()
        val packageState = shell("dumpsys package $APP_PACKAGE | grep -n 'User 0:' -A2 | head -n 3").trim()
        val accessibilityDump = shell("""dumpsys accessibility | grep -n 'Bound services\|Enabled services\|Binding services\|Crashed services' -A1 -B1""").trim()
        fail(
            "KeepAccessibilityService should bind before runtime assertions. " +
                "snapshot=$snapshot; accessibility_enabled=$accessibilityEnabled; " +
                "enabled_accessibility_services=$enabledServices; packageState=$packageState; " +
                "accessibilityDump=$accessibilityDump",
        )
    }

    private fun waitForServiceToObserveSelectedPackage(packageName: String) {
        waitUntil("KeepAccessibilityService should observe selected foreground packages before launch", SERVICE_PROPAGATION_TIMEOUT_MS) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            snapshot.observedIsKeep && snapshot.observedSelectedAppPackages.contains(packageName)
        }
    }

    private fun waitForWindowEvent(packageName: String) {
        waitUntil("KeepAccessibilityService should receive a window change event for $packageName", SERVICE_PROPAGATION_TIMEOUT_MS) {
            KeepAccessibilityServiceDebugState.read(context).lastWindowStateChangedPackage == packageName
        }
    }

    private fun shell(command: String): String {
        return device.executeShellCommand(command)
    }

    private fun resetDebugStateRetainingConnectionFlag() {
        val existingSnapshot = KeepAccessibilityServiceDebugState.read(context)
        KeepAccessibilityServiceDebugState.update(context) {
            it.copy(
                isServiceConnected = existingSnapshot.isServiceConnected,
                observedIsKeep = false,
                observedSelectedAppPackages = emptySet(),
                observedEmergencyUnlockApps = emptySet(),
                lastWindowStateChangedPackage = null,
            )
        }
    }

    private fun waitUntil(message: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
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
        const val APP_PACKAGE = "com.uiery.keep"
        const val APP_NAME = "StopIt"
        const val SERVICE_COMPONENT = "com.uiery.keep/com.uiery.keep.service.KeepAccessibilityService"
        const val PACKAGE_VISIBILITY_TIMEOUT_MS = 5_000L
        const val EMERGENCY_UNLOCK_WINDOW_MS = 60_000L
        const val UI_TIMEOUT_MS = 8_000L
        const val SERVICE_PROPAGATION_TIMEOUT_MS = 10_000L
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val MAIN_SWITCH_BAR_ID = "main_switch_bar"
        const val ALLOW_BUTTON_ID = "android:id/accessibility_permission_enable_allow_button"
        val LAUNCHABLE_PACKAGE_CANDIDATES = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.android.settings",
        )
    }
}

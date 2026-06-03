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
import java.util.regex.Pattern

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
            try {
                clearAccessibilityBlockState()
                restoreAccessibilityServiceState(initiallyEnabled = accessibilityServiceInitiallyEnabled)
                device.pressHome()
            } finally {
                Configurator.getInstance().setUiAutomationFlags(originalUiAutomationFlags)
            }
        }
    }

    @Test
    fun selectedAppForegroundBeforeServiceConnects_launchesBlockActivityAfterServiceConnects() = runBlocking {
        val blockedPackage = resolveLaunchablePackages().first()
        disableAccessibilityServiceIfEnabled()
        waitUntil("KeepAccessibilityService should be disabled before connect catch-up setup", UI_TIMEOUT_MS) {
            !isAccessibilityServiceEnabled()
        }
        configureManualKeepBlock(blockedPackage)

        launchPackage(blockedPackage)
        waitForPackageForeground(
            packageName = blockedPackage,
            message = "Expected $blockedPackage to be foreground before Accessibility service reconnects",
        )
        KeepAccessibilityServiceDebugState.reset(context)

        setAccessibilityServiceEnabled(enabled = true)
        waitUntil("KeepAccessibilityService should be enabled without leaving the blocked foreground app", UI_TIMEOUT_MS) {
            isAccessibilityServiceEnabled()
        }
        waitForServiceStatePropagation()
        waitForServiceToObserveSelectedPackage(blockedPackage)

        waitUntil(
            message = "Expected KeepAccessibilityService to catch up and request BlockActivity for foreground $blockedPackage after service connection",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            KeepAccessibilityServiceDebugState.read(context).lastLaunchedBlockPackage == blockedPackage
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

        waitUntil(
            message = "Expected KeepAccessibilityService to request BlockActivity when $blockedPackage launches",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            KeepAccessibilityServiceDebugState.read(context).lastLaunchedBlockPackage == blockedPackage
        }
    }

    @Test
    fun emergencyUnlockActive_keepsSelectedAppForegroundInsteadOfLaunchingBlockActivity() = runBlocking {
        val bypassPackage = resolveLaunchablePackages().last()
        configureManualKeepBlock(bypassPackage)
        configureEmergencyUnlock(bypassPackage)
        waitForServiceStatePropagation()
        waitForServiceToObserveSelectedPackage(bypassPackage)
        waitForServiceToObserveEmergencyUnlockPackage(bypassPackage)

        launchPackage(bypassPackage)
        waitForWindowEvent(bypassPackage)

        waitForPackageForeground(
            packageName = bypassPackage,
            message = "Expected $bypassPackage to stay foreground while emergency unlock is active",
        )
        val debugSnapshot = KeepAccessibilityServiceDebugState.read(context)
        val launchedBlockedPackage = debugSnapshot.lastLaunchedBlockPackage
        assertFalse(
            "Did not expect KeepAccessibilityService to request BlockActivity while emergency unlock is active. snapshot=$debugSnapshot",
            launchedBlockedPackage == bypassPackage,
        )
    }

    @Test
    fun uninstallAttemptWithPreventUninstallEnabled_dismissesDeleteSurface() = runBlocking {
        configurePreventUninstall(enabled = true)
        waitForServiceStatePropagation()
        waitForPreventUninstallPropagation(expected = true)

        launchSelfUninstallFlow()

        waitUntil(
            message = "Expected KeepAccessibilityService to dismiss the self-uninstall surface when prevent_uninstall is enabled",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            KeepAccessibilityServiceDebugState.read(context).lastDismissedUninstallPackage == APP_PACKAGE
        }
    }

    @Test
    fun appInfoScreenWithPreventUninstallEnabled_staysVisibleBeforeDeleteConfirmation() = runBlocking {
        configurePreventUninstall(enabled = true)
        waitForServiceStatePropagation()
        waitForPreventUninstallPropagation(expected = true)

        launchSelfAppInfoScreen(requireUninstallButton = false)
        waitForPackageForeground(
            packageName = SETTINGS_PACKAGE,
            message = "Expected the app info screen to stay foreground before uninstall confirmation",
        )

        Thread.sleep(750)

        assertTrue(
            "Expected no uninstall dismissal record before the delete confirmation surface is opened",
            KeepAccessibilityServiceDebugState.read(context).lastDismissedUninstallPackage == null,
        )
        assertTrue(
            "Expected the app info screen to stay visible before tapping uninstall",
            isPackageForeground(SETTINGS_PACKAGE),
        )
    }

    @Test
    fun uninstallAttemptWithPreventUninstallDisabled_keepsDeleteSurfaceVisible() = runBlocking {
        configurePreventUninstall(enabled = false)
        waitForServiceStatePropagation()
        waitForPreventUninstallPropagation(expected = false)

        launchSelfUninstallFlow()

        waitUntil(
            message = "Expected the uninstall surface to stay visible when prevent_uninstall is disabled",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            isUninstallSurfaceForeground()
        }
        assertTrue(
            "Expected no uninstall dismissal record when prevent_uninstall is disabled",
            KeepAccessibilityServiceDebugState.read(context).lastDismissedUninstallPackage == null,
        )
    }

    @Test
    fun cleanupRestoresAccessibilityServiceWhenItWasInitiallyDisabled() = runBlocking {
        assertTrue(
            "Expected test setup to enable KeepAccessibilityService before cleanup verification. ${accessibilityDiagnostics()}",
            isAccessibilityServiceEnabled(),
        )

        restoreAccessibilityServiceState(initiallyEnabled = false)

        waitUntil(
            message = "Expected cleanup helper to disable KeepAccessibilityService when it was initially disabled. ${accessibilityDiagnostics()}",
            timeoutMs = SERVICE_PROPAGATION_TIMEOUT_MS,
        ) {
            !isAccessibilityServiceEnabled()
        }
        assertFalse(
            "Expected KeepAccessibilityService to be disabled after cleanup restoration. ${accessibilityDiagnostics()}",
            isAccessibilityServiceEnabled(),
        )
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

    private suspend fun configurePreventUninstall(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.PREVENT_UNINSTALL] = enabled
        }
    }

    private suspend fun clearAccessibilityBlockState() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
            preferences.remove(PreferencesKey.IS_KEEP)
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.PREVENT_UNINSTALL)
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

    private fun launchSelfUninstallFlow() {
        KNOWN_UNINSTALL_PACKAGES.forEach { packageName -> shell("am force-stop $packageName") }
        launchSelfAppInfoScreen(requireUninstallButton = false)
        val uninstallButton = findUninstallButton()
        if (uninstallButton != null) {
            uninstallButton.click()
        } else {
            launchDirectSelfDeleteIntent()
        }
        waitUntil(
            message = "Expected package installer uninstall confirmation for $APP_PACKAGE to become visible",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            isUninstallSurfaceForeground()
        }
    }

    private fun launchDirectSelfDeleteIntent() {
        device.pressHome()
        shell("am start -W -a android.intent.action.DELETE -d package:$APP_PACKAGE")
    }

    private fun launchSelfAppInfoScreen(requireUninstallButton: Boolean = true) {
        shell("am force-stop $SETTINGS_PACKAGE")
        device.pressHome()
        val launchResult = shell(
            "am start -W -a ${Settings.ACTION_APPLICATION_DETAILS_SETTINGS} -d package:$APP_PACKAGE",
        )
        waitForPackageForeground(
            packageName = SETTINGS_PACKAGE,
            message = "Expected Settings app info screen to foreground for $APP_PACKAGE. launchResult=$launchResult",
        )
        if (requireUninstallButton) {
            waitForUninstallButton()
        }
    }

    private fun waitForUninstallButton(): androidx.test.uiautomator.UiObject2 {
        waitUntil(
            message = "Expected app info screen to expose an uninstall/delete action for $APP_PACKAGE. visibleTexts=${visibleSettingsTexts()}",
            timeoutMs = UI_TIMEOUT_MS,
        ) {
            findUninstallButton() != null
        }
        return findUninstallButton() ?: run {
            fail("Could not find uninstall/delete action for $APP_PACKAGE from app info screen. visibleTexts=${visibleSettingsTexts()}")
            throw AssertionError("unreachable")
        }
    }

    private fun findUninstallButton(): androidx.test.uiautomator.UiObject2? {
        val selectors = listOf(
            By.text(UNINSTALL_ACTION_PATTERN),
            By.desc(UNINSTALL_ACTION_PATTERN),
            By.textContains("Uninstall"),
            By.textContains("Delete"),
            By.textContains("Remove"),
        )
        return selectors.firstNotNullOfOrNull { selector ->
            device.findObject(selector)?.takeIf { it.isEnabled }
        }
    }

    private fun visibleSettingsTexts(): String =
        device.findObjects(By.pkg(SETTINGS_PACKAGE))
            .mapNotNull { node -> node.text?.takeIf { it.isNotBlank() } }
            .distinct()
            .take(30)
            .joinToString(" | ")

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

    private fun disableAccessibilityServiceIfEnabled() {
        if (!isAccessibilityServiceEnabled()) return

        val before = accessibilityDiagnostics()
        setAccessibilityServiceEnabled(enabled = false)

        waitUntil(
            message = "Expected KeepAccessibilityService to be disabled. before=$before; after=${accessibilityDiagnostics()}",
            timeoutMs = SERVICE_PROPAGATION_TIMEOUT_MS,
        ) {
            !isAccessibilityServiceEnabled()
        }
    }

    private fun restoreAccessibilityServiceState(initiallyEnabled: Boolean) {
        if (initiallyEnabled) return
        disableAccessibilityServiceIfEnabled()
    }

    private fun setAccessibilityServiceEnabled(enabled: Boolean) {
        val currentServices = normalizeSecureSetting(shell("settings get secure enabled_accessibility_services"))
        val retainedServices = currentServices
            .split(':')
            .filter { it.isNotBlank() && it != SERVICE_COMPONENT }
            .toMutableList()

        if (enabled) {
            retainedServices += SERVICE_COMPONENT
        }

        if (retainedServices.isEmpty()) {
            shell("settings delete secure enabled_accessibility_services")
            shell("settings put secure accessibility_enabled 0")
        } else {
            shell("settings put secure enabled_accessibility_services ${retainedServices.joinToString(":")}")
            shell("settings put secure accessibility_enabled 1")
        }
    }

    private fun accessibilityDiagnostics(): String {
        val snapshot = KeepAccessibilityServiceDebugState.read(context)
        val accessibilityEnabled = normalizeSecureSetting(shell("settings get secure accessibility_enabled"))
        val enabledServices = normalizeSecureSetting(shell("settings get secure enabled_accessibility_services"))
        val accessibilityDump = shell("""dumpsys accessibility | grep -n 'Bound services\|Enabled services\|Binding services\|Crashed services' -A1 -B1""").trim()
        return "snapshot=$snapshot; accessibility_enabled=$accessibilityEnabled; enabled_accessibility_services=$enabledServices; accessibilityDump=$accessibilityDump"
    }

    private fun normalizeSecureSetting(rawValue: String): String =
        rawValue.trim().takeUnless { it == "null" } ?: ""
    private fun openAccessibilityServiceDetails() {
        repeat(3) { attempt ->
            if (openAccessibilityServiceDetailsViaIntent()) {
                return
            }
            if (openAccessibilityServiceDetailsFromList()) {
                return
            }
            if (attempt < 2) {
                device.pressBack()
                device.waitForIdle()
                Thread.sleep(500)
            }
        }

        fail("StopIt Accessibility detail screen should open")
    }

    private fun openAccessibilityServiceDetailsFromList(): Boolean {
        shell("am force-stop $SETTINGS_PACKAGE")
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
        return waitForAccessibilityDetailScreen()
    }

    private fun openAccessibilityServiceDetailsViaIntent(): Boolean {
        shell("am force-stop $SETTINGS_PACKAGE")
        shell(
            "am start -W -a android.settings.ACCESSIBILITY_DETAILS_SETTINGS " +
                "--es android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME $SERVICE_COMPONENT",
        )
        device.waitForIdle()

        return waitForAccessibilityDetailScreen(timeoutMs = UI_TIMEOUT_MS)
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

    private fun waitForServiceToObserveEmergencyUnlockPackage(packageName: String) {
        waitUntil("KeepAccessibilityService should observe emergency unlock state before launch", SERVICE_PROPAGATION_TIMEOUT_MS) {
            KeepAccessibilityServiceDebugState.read(context)
                .observedEmergencyUnlockApps
                .contains(packageName)
        }
    }

    private fun waitForPreventUninstallPropagation(expected: Boolean) {
        waitUntil("KeepAccessibilityService should observe prevent_uninstall=$expected before uninstall assertions", SERVICE_PROPAGATION_TIMEOUT_MS) {
            KeepAccessibilityServiceDebugState.read(context).observedPreventUninstall == expected
        }
    }

    private fun waitForWindowEvent(packageName: String) {
        waitUntil("KeepAccessibilityService should receive a window change event for $packageName", SERVICE_PROPAGATION_TIMEOUT_MS) {
            KeepAccessibilityServiceDebugState.read(context).lastWindowStateChangedPackage == packageName
        }
    }

    private fun waitForAccessibilityDetailScreen(timeoutMs: Long = UI_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.res(SETTINGS_PACKAGE, MAIN_SWITCH_BAR_ID))) {
                return true
            }
            Thread.sleep(250)
        }

        return false
    }

    private fun waitForPackageVisible(
        packageName: String,
        message: String,
    ) {
        waitUntil(message, PACKAGE_VISIBILITY_TIMEOUT_MS) {
            device.hasObject(By.pkg(packageName).depth(0)) || isPackageForeground(packageName)
        }
    }

    private fun waitForPackageForeground(
        packageName: String,
        message: String,
    ) {
        waitUntil(message, PACKAGE_VISIBILITY_TIMEOUT_MS) {
            isPackageForeground(packageName)
        }
    }

    private fun isPackageForeground(packageName: String): Boolean {
        if (shell("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'")
                .contains("$packageName/")) {
            return true
        }

        return shell("dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'")
            .contains(packageName)
    }

    private fun isUninstallSurfaceForeground(): Boolean =
        KNOWN_UNINSTALL_PACKAGES.any(::isPackageForeground)

    private fun shell(command: String): String {
        return device.executeShellCommand(command)
    }

    private val appName: String
        get() = context.packageManager.getApplicationLabel(context.applicationInfo).toString()

    private fun resetDebugStateRetainingConnectionFlag() {
        val existingSnapshot = KeepAccessibilityServiceDebugState.read(context)
        KeepAccessibilityServiceDebugState.update(context) {
            it.copy(
                isServiceConnected = existingSnapshot.isServiceConnected,
                observedIsKeep = false,
                observedPreventUninstall = true,
                observedSelectedAppPackages = emptySet(),
                observedEmergencyUnlockApps = emptySet(),
                lastWindowStateChangedPackage = null,
                lastLaunchedBlockPackage = null,
                lastDismissedUninstallPackage = null,
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
        const val SERVICE_COMPONENT = "com.uiery.keep/com.uiery.keep.service.KeepAccessibilityService"
        const val PACKAGE_VISIBILITY_TIMEOUT_MS = 5_000L
        const val UNINSTALL_DISMISS_TIMEOUT_MS = 12_000L
        const val EMERGENCY_UNLOCK_WINDOW_MS = 60_000L
        const val UI_TIMEOUT_MS = 8_000L
        const val SERVICE_PROPAGATION_TIMEOUT_MS = 10_000L
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val MAIN_SWITCH_BAR_ID = "main_switch_bar"
        const val ALLOW_BUTTON_ID = "android:id/accessibility_permission_enable_allow_button"
        val UNINSTALL_ACTION_PATTERN: Pattern = Pattern.compile("(?i)(uninstall|delete|remove)(\\s+app)?")
        val KNOWN_UNINSTALL_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.samsung.android.packageinstaller",
            "com.android.vending",
        )
        val LAUNCHABLE_PACKAGE_CANDIDATES = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.android.settings",
        )
    }
}

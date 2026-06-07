package com.uiery.keep.testing

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector

class AccessibilitySettingsDetailNavigator(
    private val device: Device,
    private val settingsPackage: String,
    private val serviceComponent: String,
    private val appName: String,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) {
    fun openDetails(maxAttempts: Int = DEFAULT_MAX_ATTEMPTS): OpenResult {
        repeat(maxAttempts) { attempt ->
            if (openDetailsViaIntent()) {
                return OpenResult(opened = true, diagnostics = diagnostics())
            }
            if (openDetailsFromList()) {
                return OpenResult(opened = true, diagnostics = diagnostics())
            }
            if (attempt < maxAttempts - 1) {
                device.pressBack()
                device.waitForIdle()
                sleep(pollIntervalMs * 2)
            }
        }
        return OpenResult(opened = false, diagnostics = diagnostics())
    }

    fun requireDetailsOpen() {
        val result = openDetails()
        check(result.opened) {
            "StopIt Accessibility detail screen should open. ${result.diagnostics}"
        }
    }

    private fun openDetailsViaIntent(): Boolean {
        device.forceStopSettings(settingsPackage)
        device.startAccessibilityDetailsIntent(serviceComponent)
        device.waitForIdle()
        return waitForDetailSignal(timeoutMs)
    }

    private fun openDetailsFromList(): Boolean {
        device.forceStopSettings(settingsPackage)
        device.startAccessibilitySettingsList()
        device.waitForIdle()
        if (!device.hasText(appName)) {
            device.scrollTextIntoView(appName)
        }
        if (!device.hasText(appName)) {
            return false
        }
        if (!device.clickText(appName)) {
            return false
        }
        device.waitForIdle()
        return waitForDetailSignal(timeoutMs)
    }

    private fun waitForDetailSignal(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        do {
            if (hasDetailSignal()) {
                return true
            }
            sleep(pollIntervalMs)
        } while (System.currentTimeMillis() < deadline)
        return hasDetailSignal()
    }

    private fun hasDetailSignal(): Boolean =
        device.hasMainSwitchBar(settingsPackage) ||
            device.hasText("Use $appName") ||
            device.hasContentDescription(appName)

    private fun diagnostics(): String {
        val visibleTexts = device.visibleSettingsTexts(settingsPackage)
            .distinct()
            .take(30)
            .joinToString(" | ")
        return "foreground=${device.foregroundSnapshot()}; " +
            "visibleSettingsTexts=$visibleTexts; " +
            "accessibility_enabled=${normalizeSecureSetting(device.secureSetting("accessibility_enabled"))}; " +
            "enabled_accessibility_services=${normalizeSecureSetting(device.secureSetting("enabled_accessibility_services"))}; " +
            "accessibilityDump=${device.accessibilityDump()}"
    }

    private fun normalizeSecureSetting(rawValue: String): String =
        rawValue.trim().takeUnless { it == "null" }.orEmpty()

    private fun sleep(durationMs: Long) {
        if (durationMs > 0) {
            Thread.sleep(durationMs)
        }
    }

    data class OpenResult(
        val opened: Boolean,
        val diagnostics: String,
    )

    interface Device {
        fun forceStopSettings(settingsPackage: String)
        fun startAccessibilityDetailsIntent(serviceComponent: String)
        fun startAccessibilitySettingsList()
        fun waitForIdle()
        fun pressBack()
        fun hasMainSwitchBar(settingsPackage: String): Boolean
        fun hasText(text: String): Boolean
        fun hasContentDescription(description: String): Boolean
        fun scrollTextIntoView(text: String): Boolean
        fun clickText(text: String): Boolean
        fun foregroundSnapshot(): String
        fun visibleSettingsTexts(settingsPackage: String): List<String>
        fun secureSetting(name: String): String
        fun accessibilityDump(): String
    }

    class UiAutomatorDevice(
        private val uiDevice: UiDevice,
        private val shell: (String) -> String,
    ) : Device {
        override fun forceStopSettings(settingsPackage: String) {
            shell("am force-stop $settingsPackage")
        }

        override fun startAccessibilityDetailsIntent(serviceComponent: String) {
            shell(
                "am start -W -a android.settings.ACCESSIBILITY_DETAILS_SETTINGS " +
                    "--es android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME $serviceComponent",
            )
        }

        override fun startAccessibilitySettingsList() {
            shell("am start -W -a android.settings.ACCESSIBILITY_SETTINGS")
        }

        override fun waitForIdle() {
            uiDevice.waitForIdle()
        }

        override fun pressBack() {
            uiDevice.pressBack()
        }

        override fun hasMainSwitchBar(settingsPackage: String): Boolean =
            uiDevice.hasObject(By.res(settingsPackage, MAIN_SWITCH_BAR_ID))

        override fun hasText(text: String): Boolean = uiDevice.hasObject(By.text(text))

        override fun hasContentDescription(description: String): Boolean =
            uiDevice.hasObject(By.desc(description))

        override fun scrollTextIntoView(text: String): Boolean {
            return runCatching {
                UiScrollable(UiSelector().scrollable(true)).apply {
                    setAsVerticalList()
                }.scrollTextIntoView(text)
            }.getOrDefault(false)
        }

        override fun clickText(text: String): Boolean {
            val uiObject = uiDevice.findObject(UiSelector().text(text))
            if (!uiObject.exists()) {
                return false
            }
            uiObject.click()
            return true
        }

        override fun foregroundSnapshot(): String =
            shell("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'").trim()
                .ifBlank {
                    shell("dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'").trim()
                }

        override fun visibleSettingsTexts(settingsPackage: String): List<String> =
            uiDevice.findObjects(By.pkg(settingsPackage))
                .mapNotNull { node -> node.text?.takeIf { it.isNotBlank() } }

        override fun secureSetting(name: String): String = shell("settings get secure $name")

        override fun accessibilityDump(): String =
            shell("""dumpsys accessibility | grep -n 'Bound services\|Enabled services\|Binding services\|Crashed services' -A1 -B1""").trim()
    }

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_TIMEOUT_MS = 8_000L
        const val DEFAULT_POLL_INTERVAL_MS = 250L
        const val MAIN_SWITCH_BAR_ID = "main_switch_bar"
    }
}

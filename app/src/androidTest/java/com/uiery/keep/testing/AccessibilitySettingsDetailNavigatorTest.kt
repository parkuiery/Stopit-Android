package com.uiery.keep.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySettingsDetailNavigatorTest {
    @Test
    fun openPollsDirectIntentUntilAccessibilityDetailSignalAppearsBeforeUsingListFallback() {
        val device = FakeAccessibilitySettingsDevice(
            detailSignalByPoll = listOf(false, false, true),
        )
        val navigator = AccessibilitySettingsDetailNavigator(
            device = device,
            settingsPackage = "com.android.settings",
            serviceComponent = "com.uiery.keep.dev/com.uiery.keep.service.KeepAccessibilityService",
            appName = "StopIt",
            timeoutMs = 1_000L,
            pollIntervalMs = 1L,
        )

        val result = navigator.openDetails()

        assertTrue(result.opened)
        assertEquals(1, device.directIntentStarts)
        assertEquals(0, device.listStarts)
        assertEquals(3, device.detailSignalChecks)
    }

    @Test
    fun openUsesListFallbackAndAcceptsDetailHeaderWhenMainSwitchResourceIsLate() {
        val device = FakeAccessibilitySettingsDevice(
            detailSignalByPoll = listOf(false, false, false),
            listEntryVisible = true,
            detailTitleVisibleAfterListClick = true,
        )
        val navigator = AccessibilitySettingsDetailNavigator(
            device = device,
            settingsPackage = "com.android.settings",
            serviceComponent = "com.uiery.keep.dev/com.uiery.keep.service.KeepAccessibilityService",
            appName = "StopIt",
            timeoutMs = 1_000L,
            pollIntervalMs = 1L,
        )

        val result = navigator.openDetails()

        assertTrue(result.opened)
        assertEquals(1, device.directIntentStarts)
        assertEquals(1, device.listStarts)
        assertTrue(device.clickedServiceEntry)
    }

    @Test
    fun openFailureIncludesForegroundSettingsAndAccessibilityDiagnostics() {
        val device = FakeAccessibilitySettingsDevice(
            detailSignalByPoll = listOf(false, false, false),
            listEntryVisible = false,
            foreground = "mResumedActivity: com.android.settings/.Settings",
            visibleTexts = listOf("Accessibility", "Downloaded apps"),
            accessibilityEnabled = "1",
            enabledAccessibilityServices = "com.other/.Service",
            accessibilityDump = "Enabled services:{}",
        )
        val navigator = AccessibilitySettingsDetailNavigator(
            device = device,
            settingsPackage = "com.android.settings",
            serviceComponent = "com.uiery.keep.dev/com.uiery.keep.service.KeepAccessibilityService",
            appName = "StopIt",
            timeoutMs = 1_000L,
            pollIntervalMs = 1L,
        )

        val result = navigator.openDetails(maxAttempts = 1)

        assertFalse(result.opened)
        assertTrue(result.diagnostics.contains("foreground=mResumedActivity: com.android.settings/.Settings"))
        assertTrue(result.diagnostics.contains("visibleSettingsTexts=Accessibility | Downloaded apps"))
        assertTrue(result.diagnostics.contains("accessibility_enabled=1"))
        assertTrue(result.diagnostics.contains("enabled_accessibility_services=com.other/.Service"))
        assertTrue(result.diagnostics.contains("accessibilityDump=Enabled services:{}"))
    }

    private class FakeAccessibilitySettingsDevice(
        private val detailSignalByPoll: List<Boolean>,
        private val listEntryVisible: Boolean = false,
        private val detailTitleVisibleAfterListClick: Boolean = false,
        private val foreground: String = "",
        private val visibleTexts: List<String> = emptyList(),
        private val accessibilityEnabled: String = "0",
        private val enabledAccessibilityServices: String = "",
        private val accessibilityDump: String = "",
    ) : AccessibilitySettingsDetailNavigator.Device {
        var directIntentStarts = 0
        var listStarts = 0
        var detailSignalChecks = 0
        var clickedServiceEntry = false

        override fun forceStopSettings(settingsPackage: String) = Unit

        override fun startAccessibilityDetailsIntent(serviceComponent: String) {
            directIntentStarts += 1
        }

        override fun startAccessibilitySettingsList() {
            listStarts += 1
        }

        override fun waitForIdle() = Unit

        override fun pressBack() = Unit

        override fun hasMainSwitchBar(settingsPackage: String): Boolean {
            val value = detailSignalByPoll.getOrElse(detailSignalChecks) { false }
            detailSignalChecks += 1
            return value
        }

        override fun hasText(text: String): Boolean =
            when {
                clickedServiceEntry && detailTitleVisibleAfterListClick -> text == "Use StopIt"
                listStarts > 0 -> listEntryVisible && text == "StopIt"
                else -> false
            }

        override fun hasContentDescription(description: String): Boolean =
            clickedServiceEntry && detailTitleVisibleAfterListClick && description == "StopIt"

        override fun scrollTextIntoView(text: String): Boolean = listEntryVisible && text == "StopIt"

        override fun clickText(text: String): Boolean {
            clickedServiceEntry = text == "StopIt" && listEntryVisible
            return clickedServiceEntry
        }

        override fun foregroundSnapshot(): String = foreground

        override fun visibleSettingsTexts(settingsPackage: String): List<String> = visibleTexts

        override fun secureSetting(name: String): String = when (name) {
            "accessibility_enabled" -> accessibilityEnabled
            "enabled_accessibility_services" -> enabledAccessibilityServices
            else -> ""
        }

        override fun accessibilityDump(): String = accessibilityDump
    }
}

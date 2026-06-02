package com.uiery.keep.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAccessibilityServiceUninstallDetectionTest {
    @Test
    fun knownInstallerPackage_withApplicationIdMatch_returnsTrue() {
        assertTrue(
            shouldInterceptUninstallAttempt(
                eventPackageName = "com.android.packageinstaller",
                hasApplicationIdMatch = true,
                hasAppNameMatch = false,
            ),
        )
    }

    @Test
    fun knownInstallerPackage_withAppNameMatch_returnsTrue() {
        assertTrue(
            shouldInterceptUninstallAttempt(
                eventPackageName = "com.android.vending",
                hasApplicationIdMatch = false,
                hasAppNameMatch = true,
            ),
        )
    }

    @Test
    fun knownInstallerPackage_withEventTextMatch_returnsTrue() {
        assertTrue(
            shouldInterceptUninstallAttempt(
                eventPackageName = "com.google.android.packageinstaller",
                hasApplicationIdMatch = false,
                hasAppNameMatch = false,
                hasEventTextMatch = true,
            ),
        )
    }

    @Test
    fun permissionControllerUninstallPackage_withEventTextMatch_returnsTrue() {
        assertTrue(
            shouldInterceptUninstallAttempt(
                eventPackageName = "com.google.android.permissioncontroller",
                hasApplicationIdMatch = false,
                hasAppNameMatch = false,
                hasEventTextMatch = true,
            ),
        )
    }

    @Test
    fun knownInstallerPackage_withoutProtectedAppMarkers_returnsFalse() {
        assertFalse(
            shouldInterceptUninstallAttempt(
                eventPackageName = "com.google.android.packageinstaller",
                hasApplicationIdMatch = false,
                hasAppNameMatch = false,
            ),
        )
    }

    @Test
    fun unknownInstallerPackage_evenWithProtectedAppMarkers_returnsFalse() {
        assertFalse(
            shouldInterceptUninstallAttempt(
                eventPackageName = "com.example.settings",
                hasApplicationIdMatch = true,
                hasAppNameMatch = true,
            ),
        )
    }

    @Test
    fun knownInstallerPackageList_matchesCurrentSupportedPackages() {
        assertEquals(
            setOf(
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.google.android.permissioncontroller",
                "com.samsung.android.packageinstaller",
                "com.android.vending",
            ),
            KNOWN_UNINSTALL_PACKAGES,
        )
    }
}

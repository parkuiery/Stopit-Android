package com.uiery.keep.appselection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockExemptPackagePolicyTest {

    @Test
    fun exemptPackagesCollectsEveryResolvedDeviceRole() {
        val exempt = BlockExemptPackagePolicy.exemptPackages(
            homePackages = setOf(ONE_UI_HOME, PIXEL_LAUNCHER),
            settingsPackage = SETTINGS,
            dialerPackage = DIALER,
            smsPackage = SMS,
            nfcPaymentPackage = SAMSUNG_WALLET,
        )

        assertTrue(exempt.all.containsAll(listOf(ONE_UI_HOME, PIXEL_LAUNCHER, SETTINGS, DIALER, SMS, SAMSUNG_WALLET)))
    }

    /**
     * The split is what lets parent mode keep authority over settings while still guaranteeing the
     * supervised user can reach the home screen.
     */
    @Test
    fun onlyLaunchersAreHomePackagesAndTheRestAreEssentials() {
        val exempt = BlockExemptPackagePolicy.exemptPackages(
            homePackages = setOf(ONE_UI_HOME),
            settingsPackage = SETTINGS,
            dialerPackage = DIALER,
            smsPackage = SMS,
            nfcPaymentPackage = SAMSUNG_WALLET,
        )

        assertEquals(setOf(ONE_UI_HOME), exempt.homePackages)
        assertFalse(exempt.sensitivePackages.contains(ONE_UI_HOME))
        assertTrue(exempt.sensitivePackages.containsAll(listOf(SETTINGS, DIALER, SMS, SAMSUNG_WALLET)))
    }

    @Test
    fun exemptPackagesAlwaysIncludesCuratedPaymentPackages() {
        val exempt = BlockExemptPackagePolicy.exemptPackages(
            homePackages = emptySet(),
            settingsPackage = null,
            dialerPackage = null,
            smsPackage = null,
            nfcPaymentPackage = null,
        )

        assertTrue(exempt.sensitivePackages.containsAll(BlockExemptPackagePolicy.PAYMENT_PACKAGES))
        assertEquals(emptySet<String>(), exempt.homePackages)
    }

    @Test
    fun exemptPackagesDropsTheSystemResolverPlaceholder() {
        val exempt = BlockExemptPackagePolicy.exemptPackages(
            homePackages = setOf(ANDROID_RESOLVER, ONE_UI_HOME),
            settingsPackage = ANDROID_RESOLVER,
            dialerPackage = null,
            smsPackage = null,
            nfcPaymentPackage = null,
        )

        assertFalse(exempt.all.contains(ANDROID_RESOLVER))
        assertTrue(exempt.homePackages.contains(ONE_UI_HOME))
    }

    @Test
    fun exemptPackagesIgnoresBlankRoleResolutions() {
        val exempt = BlockExemptPackagePolicy.exemptPackages(
            homePackages = setOf("  "),
            settingsPackage = "",
            dialerPackage = "   ",
            smsPackage = null,
            nfcPaymentPackage = null,
        )

        assertEquals(BlockExemptPackagePolicy.PAYMENT_PACKAGES, exempt.all)
        assertEquals(emptySet<String>(), exempt.homePackages)
    }

    @Test
    fun exemptPackagesLabelsEachSensitiveRoleSoTheAlertCanBeSpecific() {
        val exempt = BlockExemptPackagePolicy.exemptPackages(
            homePackages = setOf(ONE_UI_HOME),
            settingsPackage = SETTINGS,
            dialerPackage = DIALER,
            smsPackage = SMS,
            nfcPaymentPackage = SAMSUNG_WALLET,
        )

        assertEquals(SensitiveAppRole.SETTINGS, exempt.sensitiveRoles[SETTINGS])
        assertEquals(SensitiveAppRole.DIALER, exempt.sensitiveRoles[DIALER])
        assertEquals(SensitiveAppRole.MESSAGING, exempt.sensitiveRoles[SMS])
        assertEquals(SensitiveAppRole.WALLET, exempt.sensitiveRoles[SAMSUNG_WALLET])
        assertEquals(setOf(DIALER), exempt.dialerPackages)
    }

    /**
     * A launcher that also resolves a sensitive role must not reach the alert — offering to block
     * something the blocking decision unconditionally exempts would be a dialog that does nothing.
     */
    @Test
    fun homeLauncherNeverAppearsAsSensitiveEvenWhenItResolvesAnotherRole() {
        val exempt = BlockExemptPackagePolicy.exemptPackages(
            homePackages = setOf(ONE_UI_HOME),
            settingsPackage = ONE_UI_HOME,
            dialerPackage = DIALER,
            smsPackage = null,
            nfcPaymentPackage = null,
        )

        assertFalse(exempt.sensitivePackages.contains(ONE_UI_HOME))
        assertTrue(exempt.homePackages.contains(ONE_UI_HOME))
        assertTrue(exempt.sensitivePackages.contains(DIALER))
    }

    @Test
    fun sensitiveSelectionsReportsOnlyTheSensitiveAppsActuallySelected() {
        val exempt = BlockExemptPackagePolicy.exemptPackages(
            homePackages = setOf(ONE_UI_HOME),
            settingsPackage = SETTINGS,
            dialerPackage = DIALER,
            smsPackage = SMS,
            nfcPaymentPackage = null,
        )

        val selections = BlockExemptPackagePolicy.sensitiveSelections(
            selectedPackages = setOf(INSTAGRAM, SMS, DIALER),
            exemptPackages = exempt,
        )

        assertEquals(
            mapOf(SMS to SensitiveAppRole.MESSAGING, DIALER to SensitiveAppRole.DIALER),
            selections,
        )
    }

    @Test
    fun sensitiveSelectionsIsEmptyWhenNothingSensitiveWasSelected() {
        val exempt = BlockExemptPackagePolicy.exemptPackages(
            homePackages = setOf(ONE_UI_HOME),
            settingsPackage = SETTINGS,
            dialerPackage = DIALER,
            smsPackage = SMS,
            nfcPaymentPackage = null,
        )

        assertTrue(
            BlockExemptPackagePolicy.sensitiveSelections(
                selectedPackages = setOf(INSTAGRAM, YOUTUBE),
                exemptPackages = exempt,
            ).isEmpty(),
        )
    }

    @Test
    fun filterBlockableRemovesExemptPackagesAndKeepsInsertionOrder() {
        val blockable = BlockExemptPackagePolicy.filterBlockable(
            packages = linkedSetOf(ONE_UI_HOME, INSTAGRAM, SAMSUNG_WALLET, YOUTUBE),
            exemptPackages = setOf(ONE_UI_HOME, SAMSUNG_WALLET),
        )

        assertEquals(listOf(INSTAGRAM, YOUTUBE), blockable.toList())
    }

    @Test
    fun filterBlockableReturnsTheSameSetWhenNothingIsExempt() {
        val packages = linkedSetOf(INSTAGRAM, YOUTUBE)

        val blockable = BlockExemptPackagePolicy.filterBlockable(
            packages = packages,
            exemptPackages = emptySet(),
        )

        assertEquals(packages, blockable)
    }

    @Test
    fun isExemptAnswersForASinglePackage() {
        val exemptPackages = setOf(ONE_UI_HOME)

        assertTrue(BlockExemptPackagePolicy.isExempt(ONE_UI_HOME, exemptPackages))
        assertFalse(BlockExemptPackagePolicy.isExempt(INSTAGRAM, exemptPackages))
    }

    @Test
    fun curatedPaymentPackagesCoverSamsungAndGoogleWallets() {
        assertTrue(BlockExemptPackagePolicy.PAYMENT_PACKAGES.contains(SAMSUNG_WALLET))
        assertTrue(BlockExemptPackagePolicy.PAYMENT_PACKAGES.contains(GOOGLE_WALLET))
    }

    private companion object {
        const val ONE_UI_HOME = "com.sec.android.app.launcher"
        const val PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val SETTINGS = "com.android.settings"
        const val DIALER = "com.samsung.android.dialer"
        const val SMS = "com.samsung.android.messaging"
        const val SAMSUNG_WALLET = "com.samsung.android.spay"
        const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"
        const val ANDROID_RESOLVER = "android"
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"
    }
}

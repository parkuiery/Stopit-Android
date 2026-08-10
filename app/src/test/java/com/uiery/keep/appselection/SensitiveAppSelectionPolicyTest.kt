package com.uiery.keep.appselection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveAppSelectionPolicyTest {

    private val exempt = BlockExemptPackagePolicy.exemptPackages(
        homePackages = setOf(LAUNCHER),
        settingsPackage = SETTINGS,
        dialerPackage = DIALER,
        smsPackage = SMS,
        nfcPaymentPackage = WALLET,
    )

    private val appNames = mapOf(
        SETTINGS to "설정",
        DIALER to "전화",
        SMS to "메시지",
        WALLET to "지갑",
        INSTAGRAM to "Instagram",
    )

    @Test
    fun confirmationListsOnlyTheSensitiveAppsThatWereSelected() {
        val pending = SensitiveAppSelectionPolicy.pendingConfirmations(
            selectedPackages = setOf(INSTAGRAM, SMS),
            appNamesByPackage = appNames,
            exemptPackages = exempt,
        )

        assertEquals(
            listOf(SensitiveAppSelection(SMS, "메시지", SensitiveAppRole.MESSAGING)),
            pending,
        )
    }

    /** Same list every time, running from the cost most people accept to the hardest to undo. */
    @Test
    fun confirmationOrdersByRoleSoTheListIsStable() {
        val pending = SensitiveAppSelectionPolicy.pendingConfirmations(
            selectedPackages = setOf(SETTINGS, WALLET, DIALER, SMS),
            appNamesByPackage = appNames,
            exemptPackages = exempt,
        )

        assertEquals(
            listOf(
                SensitiveAppRole.MESSAGING,
                SensitiveAppRole.DIALER,
                SensitiveAppRole.WALLET,
                SensitiveAppRole.SETTINGS,
            ),
            pending.map { it.role },
        )
    }

    /**
     * A selection with nothing sensitive in it must save straight through. A confirmation that
     * appears for every save teaches the user to dismiss it unread.
     */
    @Test
    fun ordinaryAppsNeedNoConfirmation() {
        assertTrue(
            SensitiveAppSelectionPolicy.pendingConfirmations(
                selectedPackages = setOf(INSTAGRAM),
                appNamesByPackage = appNames,
                exemptPackages = exempt,
            ).isEmpty(),
        )
    }

    /** The launcher is unconditionally exempt, so it never reaches a dialog offering to block it. */
    @Test
    fun homeLauncherIsNeverOfferedForConfirmation() {
        assertTrue(
            SensitiveAppSelectionPolicy.pendingConfirmations(
                selectedPackages = setOf(LAUNCHER),
                appNamesByPackage = appNames + (LAUNCHER to "홈"),
                exemptPackages = exempt,
            ).isEmpty(),
        )
    }

    /** A line the user cannot read is worse than one line short. */
    @Test
    fun packagesWithoutALabelAreDroppedRatherThanShownAsPackageIds() {
        val pending = SensitiveAppSelectionPolicy.pendingConfirmations(
            selectedPackages = setOf(SMS, WALLET),
            appNamesByPackage = mapOf(SMS to "메시지"),
            exemptPackages = exempt,
        )

        assertEquals(listOf(SMS), pending.map { it.packageName })
    }

    @Test
    fun excludingSensitiveAppsKeepsEverythingElse() {
        val remaining = SensitiveAppSelectionPolicy.withoutSensitiveApps(
            selectedPackages = setOf(INSTAGRAM, SMS, DIALER, SETTINGS),
            exemptPackages = exempt,
        )

        assertEquals(setOf(INSTAGRAM), remaining)
    }

    private companion object {
        const val LAUNCHER = "com.sec.android.app.launcher"
        const val SETTINGS = "com.android.settings"
        const val DIALER = "com.samsung.android.dialer"
        const val SMS = "com.samsung.android.messaging"
        const val WALLET = "com.samsung.android.spay"
        const val INSTAGRAM = "com.instagram.android"
    }
}

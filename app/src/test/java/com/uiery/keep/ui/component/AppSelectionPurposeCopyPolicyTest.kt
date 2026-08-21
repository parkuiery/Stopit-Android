package com.uiery.keep.ui.component

import com.uiery.keep.appselection.BlockExemptPackages
import com.uiery.keep.appselection.SensitiveAppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet is shared between "pick what to stop" and "pick what stays open". Those two readings of
 * the same list are opposites, so the copy and the confirmations have to follow the purpose.
 */
class AppSelectionPurposeCopyPolicyTest {

    @Test
    fun allowPurposeIsHeadedAsAllowedAppsWhicheverTabSetTheSheetHas() {
        assertEquals(
            AppSelectionHeading.AllowedApps,
            appSelectionHeading(purpose = AppSelectionPurpose.Allow, websiteSelectionEnabled = false),
        )
        assertEquals(
            AppSelectionHeading.AllowedApps,
            appSelectionHeading(purpose = AppSelectionPurpose.Allow, websiteSelectionEnabled = true),
        )
    }

    @Test
    fun blockPurposeKeepsTheExistingLockTargetAndActivityHeadings() {
        assertEquals(
            AppSelectionHeading.LockTargets,
            appSelectionHeading(purpose = AppSelectionPurpose.Block, websiteSelectionEnabled = true),
        )
        assertEquals(
            AppSelectionHeading.Activities,
            appSelectionHeading(purpose = AppSelectionPurpose.Block, websiteSelectionEnabled = false),
        )
    }

    /**
     * The confirmation warns that the selected apps are about to be blocked, and its dismiss action
     * drops them from the selection. Under an allowlist both halves are inverted: adding the dialer
     * keeps it reachable, and "exclude" is what actually blocks it.
     */
    @Test
    fun allowPurposeDoesNotConfirmSensitiveAppsAsIfSelectingThemBlockedThem() {
        assertFalse(
            requiresSensitiveBlockConfirmation(
                purpose = AppSelectionPurpose.Allow,
                selectedPackages = setOf(DIALER),
                exemptPackages = exemptPackages(),
            ),
        )
    }

    @Test
    fun blockPurposeStillConfirmsSensitiveSelections() {
        assertTrue(
            requiresSensitiveBlockConfirmation(
                purpose = AppSelectionPurpose.Block,
                selectedPackages = setOf(DIALER),
                exemptPackages = exemptPackages(),
            ),
        )
        assertFalse(
            requiresSensitiveBlockConfirmation(
                purpose = AppSelectionPurpose.Block,
                selectedPackages = setOf("com.example.plain"),
                exemptPackages = exemptPackages(),
            ),
        )
    }

    private fun exemptPackages() = BlockExemptPackages(
        homePackages = setOf("com.example.launcher"),
        sensitiveRoles = mapOf(DIALER to SensitiveAppRole.DIALER),
    )

    private companion object {
        const val DIALER = "com.example.dialer"
    }
}

package com.uiery.keep.appselection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockExemptRoleCachePolicyTest {

    @Test
    fun aRoleStaysReusableUntilTheTtlElapses() {
        val resolvedAt = 10_000L

        assertTrue(BlockExemptRoleCachePolicy.isFresh(resolvedAt, resolvedAt))
        assertTrue(
            BlockExemptRoleCachePolicy.isFresh(
                resolvedAt,
                resolvedAt + BlockExemptRoleCachePolicy.TTL_MS - 1,
            ),
        )
    }

    @Test
    fun aRoleIsReResolvedOnceTheTtlElapses() {
        val resolvedAt = 10_000L

        assertFalse(BlockExemptRoleCachePolicy.isFresh(resolvedAt, resolvedAt + BlockExemptRoleCachePolicy.TTL_MS))
    }

    /**
     * elapsedRealtime is monotonic so this should not happen, but treating a backwards clock as
     * "fresh" would pin the cache for the life of the process — the exact failure being fixed.
     */
    @Test
    fun aBackwardsClockForcesReResolutionRatherThanPinningTheCache() {
        assertFalse(BlockExemptRoleCachePolicy.isFresh(resolvedAtElapsedRealtime = 10_000L, nowElapsedRealtime = 9_999L))
    }

    @Test
    fun theFirstResolutionIsKeptAsIs() {
        val resolved = ReassignableDeviceRoles(dialerPackage = SAMSUNG_DIALER, smsPackage = null)

        assertEquals(resolved, BlockExemptRoleCachePolicy.keepingResolvedRoles(resolved, previous = null))
    }

    /**
     * The reassignment this whole cache exists to notice.
     */
    @Test
    fun aRoleThatMovedToAnotherAppReplacesTheOldHolder() {
        val previous = ReassignableDeviceRoles(dialerPackage = SAMSUNG_DIALER)
        val resolved = ReassignableDeviceRoles(dialerPackage = GOOGLE_DIALER)

        val merged = BlockExemptRoleCachePolicy.keepingResolvedRoles(resolved, previous)

        assertEquals(GOOGLE_DIALER, merged.dialerPackage)
    }

    /**
     * Taking an empty lookup at face value would drop the in-call exemption and put the block
     * screen over a ringing phone.
     */
    @Test
    fun aRoleThatFailedToResolveKeepsTheLastKnownHolder() {
        val previous = ReassignableDeviceRoles(
            settingsPackage = SETTINGS,
            dialerPackage = SAMSUNG_DIALER,
            smsPackage = SMS,
            nfcPaymentPackage = SAMSUNG_WALLET,
        )

        val merged = BlockExemptRoleCachePolicy.keepingResolvedRoles(
            resolved = ReassignableDeviceRoles(),
            previous = previous,
        )

        assertEquals(previous, merged)
    }

    @Test
    fun rolesAreKeptOrReplacedIndependentlyOfEachOther() {
        val previous = ReassignableDeviceRoles(
            settingsPackage = SETTINGS,
            dialerPackage = SAMSUNG_DIALER,
            smsPackage = SMS,
        )

        val merged = BlockExemptRoleCachePolicy.keepingResolvedRoles(
            resolved = ReassignableDeviceRoles(settingsPackage = SETTINGS, dialerPackage = GOOGLE_DIALER),
            previous = previous,
        )

        assertEquals(SETTINGS, merged.settingsPackage)
        assertEquals(GOOGLE_DIALER, merged.dialerPackage)
        assertEquals(SMS, merged.smsPackage)
    }

    private companion object {
        const val SETTINGS = "com.android.settings"
        const val SAMSUNG_DIALER = "com.samsung.android.dialer"
        const val GOOGLE_DIALER = "com.google.android.dialer"
        const val SMS = "com.samsung.android.messaging"
        const val SAMSUNG_WALLET = "com.samsung.android.spay"
    }
}

package com.uiery.keep

import com.uiery.keep.feature.lockhistory.LockHistoryRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAppNavigationPolicyTest {
    @Test
    fun historyDomainUsesLockHistoryAsCanonicalTopLevelRoute() {
        assertEquals(LockHistoryRoute, canonicalHistoryRoute())
        assertFalse(shouldRegisterLegacyHistoryRoute())
    }

    @Test
    fun devToolRouteIsAvailableOnlyForDevDebugBuilds() {
        assertTrue(
            shouldRegisterDevToolRoute(
                flavor = "dev",
                isDebug = true,
            )
        )

        assertFalse(
            shouldRegisterDevToolRoute(
                flavor = "prod",
                isDebug = true,
            )
        )
        assertFalse(
            shouldRegisterDevToolRoute(
                flavor = "prod",
                isDebug = false,
            )
        )
        assertFalse(
            shouldRegisterDevToolRoute(
                flavor = "dev",
                isDebug = false,
            )
        )
    }
}

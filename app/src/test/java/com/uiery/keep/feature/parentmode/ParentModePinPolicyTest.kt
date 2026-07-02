package com.uiery.keep.feature.parentmode

import com.uiery.keep.analytics.AnalyticsParentModePinResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ParentModePinPolicyTest {
    @Test
    fun pinStateMapsToPrivacySafeAnalyticsResultWithoutPinValueOrLength() {
        assertEquals(
            AnalyticsParentModePinResult.SUCCESS,
            ParentModePolicy.pinResult(ParentModePinState.Verified),
        )
        assertEquals(
            AnalyticsParentModePinResult.FAILURE,
            ParentModePolicy.pinResult(ParentModePinState.Failed),
        )
        assertEquals(
            AnalyticsParentModePinResult.NOT_CONFIGURED,
            ParentModePolicy.pinResult(ParentModePinState.NotConfigured),
        )
    }
}

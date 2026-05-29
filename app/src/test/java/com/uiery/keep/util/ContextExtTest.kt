package com.uiery.keep.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextExtTest {

    @Test
    fun enabledAccessibilityServicesMatchesExactKeepServiceComponent() {
        assertTrue(
            enabledAccessibilityServicesContainsService(
                enabledServices = "com.other/.Helper:com.uiery.keep/com.uiery.keep.service.KeepAccessibilityService",
                serviceComponent = "com.uiery.keep/com.uiery.keep.service.KeepAccessibilityService",
            ),
        )
    }

    @Test
    fun enabledAccessibilityServicesRejectsPackageSubstringFalsePositive() {
        assertFalse(
            enabledAccessibilityServicesContainsService(
                enabledServices = "com.uiery.keep.fake/com.fake.Service:com.other/.Helper",
                serviceComponent = "com.uiery.keep/com.uiery.keep.service.KeepAccessibilityService",
            ),
        )
    }

    @Test
    fun enabledAccessibilityServicesRejectsBlankOrMissingValue() {
        assertFalse(
            enabledAccessibilityServicesContainsService(
                enabledServices = null,
                serviceComponent = "com.uiery.keep/com.uiery.keep.service.KeepAccessibilityService",
            ),
        )
        assertFalse(
            enabledAccessibilityServicesContainsService(
                enabledServices = "  ",
                serviceComponent = "com.uiery.keep/com.uiery.keep.service.KeepAccessibilityService",
            ),
        )
    }
}

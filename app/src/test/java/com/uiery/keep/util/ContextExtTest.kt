package com.uiery.keep.util

import android.app.Activity
import android.content.ContextWrapper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

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
    fun enabledAccessibilityServicesAcceptsShortClassNameForMatchingService() {
        assertTrue(
            enabledAccessibilityServicesContainsService(
                enabledServices = "com.uiery.keep/.service.KeepAccessibilityService:com.other/.Helper",
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

    @Test
    fun findActivityUnwrapsNestedContextWrappers() {
        val activity = Mockito.mock(Activity::class.java)
        val inner = Mockito.mock(ContextWrapper::class.java)
        val outer = Mockito.mock(ContextWrapper::class.java)
        Mockito.`when`(inner.baseContext).thenReturn(activity)
        Mockito.`when`(outer.baseContext).thenReturn(inner)

        assertSame(activity, outer.findActivity())
    }

    @Test
    fun findActivityReturnsNullWhenNoActivityExistsInContextChain() {
        val wrapped = Mockito.mock(ContextWrapper::class.java)
        Mockito.`when`(wrapped.baseContext).thenReturn(null)

        assertNull(wrapped.findActivity())
    }
}

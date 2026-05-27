package com.uiery.keep.analytics

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsBackendFactoryTest {
    @Test
    fun createReturnsNoOpBackendWhenAnalyticsInitThrowsNoSuchMethodError() {
        val backend = AnalyticsBackendFactory.create {
            throw NoSuchMethodError("android.content.Context.getAttributionSource")
        }

        assertTrue(backend is NoOpAnalyticsBackend)
    }

    @Test
    fun createReturnsProducedBackendWhenAnalyticsInitSucceeds() {
        val expected = RecordingAnalyticsBackend()

        val backend = AnalyticsBackendFactory.create {
            expected
        }

        assertSame(expected, backend)
    }

    private class RecordingAnalyticsBackend : AnalyticsBackend {
        override fun logEvent(name: String, params: Map<String, Any?>) = Unit

        override fun logScreenView(screenName: String) = Unit

        override fun setUserProperty(name: String, value: String) = Unit
    }
}

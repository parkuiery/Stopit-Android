package com.uiery.keep.feature.menu

import com.uiery.keep.analytics.FirebaseKeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsEvent
import com.uiery.keep.analytics.KeepAnalyticsParam
import com.uiery.keep.analytics.AnalyticsBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportContactFallbackTest {
    @Test
    fun supportContactDiagnosticsIncludeOnlyVersionOsAndDeviceModel() {
        val diagnostics = buildSupportContactDiagnostics(
            versionName = "1.8.0",
            androidRelease = "15",
            sdkInt = 35,
            deviceModel = "Pixel 8",
        )

        assertTrue(diagnostics.contains("Version 1.8.0"))
        assertTrue(diagnostics.contains("Android OS 15 (35),Pixel 8"))
        assertFalse(diagnostics.contains("com.example.blocked"))
        assertFalse(diagnostics.contains("routine"))
        assertFalse(diagnostics.contains("reason"))
        assertFalse(diagnostics.contains("lock_history"))
    }

    @Test
    fun supportClipboardFallbackTextIncludesSupportEmailAndDiagnostics() {
        val fallbackText = buildSupportContactFallbackText(
            supportEmail = "support@example.com",
            diagnostics = "Version 1.8.0\nAndroid OS 15 (35),Pixel 8",
        )

        assertTrue(fallbackText.contains("support@example.com"))
        assertTrue(fallbackText.contains("Version 1.8.0"))
        assertTrue(fallbackText.contains("Android OS 15 (35),Pixel 8"))
    }

    @Test
    fun supportContactAnalyticsUsesPrivacySafeSurfaceAndFallbackType() {
        val backend = RecordingAnalyticsBackend()
        val analytics = FirebaseKeepAnalytics(backend)

        analytics.trackSupportContactStarted(surface = SupportContactSurface.MENU)
        analytics.trackSupportContactFallbackUsed(
            surface = SupportContactSurface.MENU,
            fallbackType = SupportContactFallbackType.CLIPBOARD,
        )

        assertEquals(
            listOf(
                RecordedEvent(
                    name = KeepAnalyticsEvent.SUPPORT_CONTACT_STARTED,
                    params = mapOf(KeepAnalyticsParam.SURFACE to "menu"),
                ),
                RecordedEvent(
                    name = KeepAnalyticsEvent.SUPPORT_CONTACT_FALLBACK_USED,
                    params = mapOf(
                        KeepAnalyticsParam.SURFACE to "menu",
                        KeepAnalyticsParam.FALLBACK_TYPE to "clipboard",
                    ),
                ),
            ),
            backend.events,
        )
    }
}

private data class RecordedEvent(
    val name: String,
    val params: Map<String, Any?>,
)

private class RecordingAnalyticsBackend : AnalyticsBackend {
    val events = mutableListOf<RecordedEvent>()

    override fun logEvent(name: String, params: Map<String, Any?>) {
        events += RecordedEvent(name = name, params = params)
    }

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(name: String, value: String) = Unit
}

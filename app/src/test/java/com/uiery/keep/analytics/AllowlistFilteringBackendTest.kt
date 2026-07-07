package com.uiery.keep.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AllowlistFilteringBackendTest {
    private val delegate = AllowlistRecordingBackend()
    private val backend = AllowlistFilteringBackend(delegate, allowlist = setOf("allowed_event"))

    @Test
    fun forwardsAllowlistedEvents() {
        backend.logEvent("allowed_event", mapOf("k" to 1))

        assertEquals(listOf("allowed_event" to mapOf<String, Any?>("k" to 1)), delegate.events)
    }

    @Test
    fun dropsNonAllowlistedEvents() {
        backend.logEvent("blocked_event", mapOf("k" to 1))

        assertEquals(emptyList<Pair<String, Map<String, Any?>>>(), delegate.events)
    }

    @Test
    fun dropsScreenViewsEntirely() {
        backend.logScreenView("HomeScreen")

        assertEquals(emptyList<String>(), delegate.screens)
    }

    @Test
    fun alwaysForwardsUserProperties() {
        backend.setUserProperty("plan", "free")

        assertEquals(listOf("plan" to "free"), delegate.userProps)
    }
}

private class AllowlistRecordingBackend : AnalyticsBackend {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    val screens = mutableListOf<String>()
    val userProps = mutableListOf<Pair<String, String>>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        events += name to params
    }

    override fun logScreenView(screenName: String) {
        screens += screenName
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        userProps += name to value
    }
}

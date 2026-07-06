package com.uiery.keep.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class CompositeAnalyticsBackendTest {
    @Test
    fun fansOutEveryCallToAllBackends() {
        val first = CompositeRecordingBackend()
        val second = CompositeRecordingBackend()
        val composite = CompositeAnalyticsBackend(listOf(first, second))

        composite.logEvent("evt", mapOf("k" to 1))
        composite.logScreenView("HomeScreen")
        composite.setUserProperty("plan", "free")

        listOf(first, second).forEach { backend ->
            assertEquals(listOf("evt" to mapOf<String, Any?>("k" to 1)), backend.events)
            assertEquals(listOf("HomeScreen"), backend.screens)
            assertEquals(listOf("plan" to "free"), backend.userProps)
        }
    }

    @Test
    fun oneBackendFailingDoesNotStopOthers() {
        val throwing = object : AnalyticsBackend {
            override fun logEvent(name: String, params: Map<String, Any?>): Unit = throw RuntimeException("boom")

            override fun logScreenView(screenName: String): Unit = throw RuntimeException("boom")

            override fun setUserProperty(name: String, value: String): Unit = throw RuntimeException("boom")
        }
        val healthy = CompositeRecordingBackend()
        val composite = CompositeAnalyticsBackend(listOf(throwing, healthy))

        composite.logEvent("evt", emptyMap())
        composite.logScreenView("HomeScreen")
        composite.setUserProperty("plan", "free")

        assertEquals(listOf("evt" to emptyMap<String, Any?>()), healthy.events)
        assertEquals(listOf("HomeScreen"), healthy.screens)
        assertEquals(listOf("plan" to "free"), healthy.userProps)
    }
}

private class CompositeRecordingBackend : AnalyticsBackend {
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

package com.uiery.keep.analytics.amplitude

import com.uiery.keep.analytics.AnalyticsBackend
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetCappedBackendTest {
    @Test
    fun forwardsWhileBudgetAllowsThenDrops() {
        val delegate = BudgetRecordingBackend()
        var remaining = 2
        val budget = AmplitudeEventBudget { if (remaining > 0) { remaining--; true } else false }
        val backend = BudgetCappedBackend(delegate, budget)

        repeat(4) { backend.logEvent("evt", emptyMap()) }

        assertEquals(2, delegate.events.size)
    }

    @Test
    fun userPropertiesAlwaysForwardedRegardlessOfBudget() {
        val delegate = BudgetRecordingBackend()
        val backend = BudgetCappedBackend(delegate, budget = AmplitudeEventBudget { false })

        backend.setUserProperty("plan", "free")

        assertEquals(listOf("plan" to "free"), delegate.userProps)
    }

    private class BudgetRecordingBackend : AnalyticsBackend {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        val userProps = mutableListOf<Pair<String, String>>()

        override fun logEvent(
            name: String,
            params: Map<String, Any?>,
        ) {
            events += name to params
        }

        override fun logScreenView(screenName: String) = Unit

        override fun setUserProperty(
            name: String,
            value: String,
        ) {
            userProps += name to value
        }
    }
}

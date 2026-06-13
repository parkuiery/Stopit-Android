package com.uiery.keep.service

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityRuntimeFlowRecoveryTest {
    @Test
    fun runtimeFlowRecoveryResubscribesAfterTransientFailureAndPreservesLastKnownEmission() = runBlocking {
        var subscriptions = 0
        val recoveryEvents = mutableListOf<AccessibilityRuntimeFlowRecoveryEvent>()
        val values = flow {
            subscriptions += 1
            if (subscriptions == 1) {
                emit("last-known-good")
                throw IllegalStateException("transient datastore read")
            }
            emit("recovered-snapshot")
        }
            .withAccessibilityRuntimeRecovery(
                source = AccessibilityRuntimeFlowSource.Routines,
                onRecoveryEvent = recoveryEvents::add,
                delayMillis = {},
            )
            .take(2)
            .toList()

        assertEquals(listOf("last-known-good", "recovered-snapshot"), values)
        assertEquals(2, subscriptions)
        assertEquals(
            listOf(
                AccessibilityRuntimeFlowRecoveryEvent(
                    source = AccessibilityRuntimeFlowSource.Routines,
                    attempt = 0L,
                    retryDelayMillis = 1_000L,
                    errorType = "IllegalStateException",
                ),
            ),
            recoveryEvents,
        )
    }

    @Test
    fun recoveryDelayBacksOffButStaysBoundedForLongLivedAccessibilityService() {
        assertEquals(1_000L, AccessibilityRuntimeFlowRecoveryPolicy.retryDelayMillis(attempt = 0L))
        assertEquals(2_000L, AccessibilityRuntimeFlowRecoveryPolicy.retryDelayMillis(attempt = 1L))
        assertEquals(30_000L, AccessibilityRuntimeFlowRecoveryPolicy.retryDelayMillis(attempt = 100L))
    }
}

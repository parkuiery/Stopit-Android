package com.uiery.keep.testing

/**
 * Shared condition-based wait utilities for instrumentation tests.
 *
 * Runtime smoke tests should describe the condition they are waiting for so
 * flakes fail with actionable evidence instead of hiding behind raw sleeps.
 */
object AndroidTestConditionWaiter {
    const val DEFAULT_TIMEOUT_MS = 5_000L
    const val DEFAULT_POLL_INTERVAL_MS = 100L

    fun waitUntil(
        message: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            pause(pollIntervalMs, reason = message)
        }
        if (!condition()) {
            throw AssertionError(message)
        }
    }

    fun pause(durationMs: Long, reason: String) {
        if (durationMs <= 0L) {
            return
        }
        Thread.sleep(durationMs)
    }
}

package com.uiery.keep.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns idempotent bootstrap for long-lived AccessibilityService runtime collectors.
 *
 * Android can invoke onServiceConnected() more than once for the same service instance. The
 * collectors started from that callback observe hot runtime state for the lifetime of the service,
 * so starting them again would duplicate foreground re-evaluation, timer scheduling, and emergency
 * unlock notification sync side effects.
 */
internal class AccessibilityRuntimeCollectorBootstrap {
    private val started = AtomicBoolean(false)

    fun startIfNeeded(startCollectors: () -> Unit): StartResult =
        if (started.compareAndSet(false, true)) {
            startCollectors()
            StartResult.Started
        } else {
            StartResult.AlreadyStarted
        }

    fun reset() {
        started.set(false)
    }

    enum class StartResult {
        Started,
        AlreadyStarted,
    }
}

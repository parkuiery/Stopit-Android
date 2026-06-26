package com.uiery.keep.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityRuntimeCollectorBootstrapTest {
    @Test
    fun startIfNeededRunsCollectorsOnlyOnceAcrossRepeatedServiceConnections() {
        val bootstrap = AccessibilityRuntimeCollectorBootstrap()
        var starts = 0

        assertEquals(AccessibilityRuntimeCollectorBootstrap.StartResult.Started, bootstrap.startIfNeeded { starts += 1 })
        assertEquals(AccessibilityRuntimeCollectorBootstrap.StartResult.AlreadyStarted, bootstrap.startIfNeeded { starts += 1 })
        assertEquals(AccessibilityRuntimeCollectorBootstrap.StartResult.AlreadyStarted, bootstrap.startIfNeeded { starts += 1 })

        assertEquals(1, starts)
    }

    @Test
    fun resetAllowsCollectorsToStartAgainAfterServiceDestroy() {
        val bootstrap = AccessibilityRuntimeCollectorBootstrap()
        var starts = 0

        bootstrap.startIfNeeded { starts += 1 }
        bootstrap.reset()
        assertEquals(AccessibilityRuntimeCollectorBootstrap.StartResult.Started, bootstrap.startIfNeeded { starts += 1 })

        assertEquals(2, starts)
    }
}

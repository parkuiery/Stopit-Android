package com.uiery.keep.service

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAccessibilityServiceEmergencyUnlockCleanupTest {

    @Test
    fun cleanupLatchIsReleasedWhenRuntimeStateClearFails() = runBlocking {
        val latch = AtomicBoolean(true)

        val failure = runCatching {
            clearExpiredEmergencyUnlockRuntimeStateAndReleaseLatch(latch) {
                throw IllegalStateException("clear failed")
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(latch.get())
        assertTrue(latch.compareAndSet(false, true))
    }

    @Test
    fun cleanupLatchIsReleasedWhenRuntimeStateClearIsCancelled() = runBlocking {
        val latch = AtomicBoolean(true)

        val failure = runCatching {
            clearExpiredEmergencyUnlockRuntimeStateAndReleaseLatch(latch) {
                throw CancellationException("clear cancelled")
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertFalse(latch.get())
        assertTrue(latch.compareAndSet(false, true))
    }
}

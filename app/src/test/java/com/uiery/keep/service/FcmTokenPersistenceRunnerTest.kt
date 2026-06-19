package com.uiery.keep.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmTokenPersistenceRunnerTest {

    @Test
    fun launchReportsPersistenceFailureWithoutThrowing() = runBlocking {
        val reporter = RecordingFcmTokenPersistenceFailureReporter()
        val job = FcmTokenPersistenceRunner.launch(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            failureReporter = reporter,
        ) {
            throw IllegalStateException("datastore write failed")
        }

        job.join()

        assertTrue(job.isCompleted)
        assertEquals(
            listOf(IllegalStateException::class.java.name),
            reporter.failures.map { it.causeClassName },
        )
    }

    @Test
    fun launchDoesNotReportSuccessfulPersistence() = runBlocking {
        val reporter = RecordingFcmTokenPersistenceFailureReporter()
        val job = FcmTokenPersistenceRunner.launch(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            failureReporter = reporter,
        ) {
            Unit
        }

        job.join()

        assertTrue(job.isCompleted)
        assertTrue(reporter.failures.isEmpty())
    }

    @Test
    fun launchRetriesPersistenceFailureBeforeReporting() = runBlocking {
        val reporter = RecordingFcmTokenPersistenceFailureReporter()
        var attempts = 0
        val job = FcmTokenPersistenceRunner.launch(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            failureReporter = reporter,
            maxAttempts = 3,
            retryDelayMillis = 0,
        ) {
            attempts += 1
            if (attempts < 3) {
                throw IllegalStateException("failed while saving raw-token-123")
            }
        }

        job.join()

        assertTrue(job.isCompleted)
        assertEquals(3, attempts)
        assertTrue(reporter.failures.isEmpty())
    }

    @Test
    fun crashlyticsExceptionMessageDoesNotContainRawTokenOrCauseMessage() {
        val exception = FcmTokenPersistenceException(
            FcmTokenPersistenceFailure(
                cause = IllegalStateException("failed while saving raw-token-123"),
            ),
        )

        assertFalse(exception.message.orEmpty().contains("raw-token-123"))
        assertFalse(exception.message.orEmpty().contains("failed while saving"))
        assertTrue(exception.message.orEmpty().contains(IllegalStateException::class.java.name))
    }
}

private class RecordingFcmTokenPersistenceFailureReporter : FcmTokenPersistenceFailureReporter {
    val failures = mutableListOf<FcmTokenPersistenceFailure>()

    override fun record(failure: FcmTokenPersistenceFailure) {
        failures += failure
    }
}

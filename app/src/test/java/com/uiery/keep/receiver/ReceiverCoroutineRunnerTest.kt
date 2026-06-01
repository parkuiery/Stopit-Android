package com.uiery.keep.receiver

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverCoroutineRunnerTest {

    private class RecordingReceiverFailureReporter : ReceiverFailureReporter {
        val failures = mutableListOf<ReceiverFailure>()

        override fun record(failure: ReceiverFailure) {
            failures += failure
        }
    }

    @Test
    fun launchReceiverWorkReportsReceiverFailureWithReceiverNameAndCause() = runBlocking {
        val finishCount = AtomicInteger(0)
        val reporter = RecordingReceiverFailureReporter()
        val expected = IllegalStateException("notification helper failed")

        val job = ReceiverCoroutineRunner.launch(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            receiverName = "RoutineAlarmReceiver",
            finish = { finishCount.incrementAndGet() },
            failureReporter = reporter,
        ) {
            throw expected
        }

        job.join()

        assertEquals(1, finishCount.get())
        assertEquals(1, reporter.failures.size)
        assertEquals("RoutineAlarmReceiver", reporter.failures.single().receiverName)
        assertSame(expected, reporter.failures.single().cause)
        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
    }

    @Test
    fun launchReceiverWorkFinishesPendingResultWhenWorkSucceeds() = runBlocking {
        val finishCount = AtomicInteger(0)
        var workRan = false

        val job = ReceiverCoroutineRunner.launch(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            receiverName = "TestReceiver",
            finish = { finishCount.incrementAndGet() },
            onFailure = { error("unexpected failure: $it") },
        ) {
            workRan = true
        }

        job.join()

        assertTrue(workRan)
        assertEquals(1, finishCount.get())
        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
    }

    @Test
    fun launchReceiverWorkContainsNonCancellationExceptionAndFinishesPendingResult() = runBlocking {
        val finishCount = AtomicInteger(0)
        val failures = mutableListOf<Throwable>()
        val expected = IllegalStateException("dao unavailable")

        val job = ReceiverCoroutineRunner.launch(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            receiverName = "TestReceiver",
            finish = { finishCount.incrementAndGet() },
            onFailure = { failures += it },
        ) {
            throw expected
        }

        job.join()

        assertEquals(1, finishCount.get())
        assertEquals(1, failures.size)
        assertSame(expected, failures.single())
        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
    }

    @Test
    fun launchReceiverWorkDoesNotCancelSiblingReceiverWorkWhenOneReceiverFails() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val finishCount = AtomicInteger(0)
        val failures = mutableListOf<Throwable>()
        var siblingWorkRan = false

        val failingJob = ReceiverCoroutineRunner.launch(
            scope = scope,
            receiverName = "FailingReceiver",
            finish = { finishCount.incrementAndGet() },
            onFailure = { failures += it },
        ) {
            throw IllegalArgumentException("notification helper failed")
        }
        val siblingJob = ReceiverCoroutineRunner.launch(
            scope = scope,
            receiverName = "SiblingReceiver",
            finish = { finishCount.incrementAndGet() },
            onFailure = { error("unexpected sibling failure: $it") },
        ) {
            siblingWorkRan = true
        }

        joinAll(failingJob, siblingJob)

        assertEquals(2, finishCount.get())
        assertEquals(1, failures.size)
        assertTrue(siblingWorkRan)
        assertTrue(failingJob.isCompleted)
        assertFalse(failingJob.isCancelled)
        assertTrue(siblingJob.isCompleted)
        assertFalse(siblingJob.isCancelled)
    }

    @Test
    fun launchReceiverWorkKeepsCancellationSemanticsButStillFinishesPendingResult() = runBlocking {
        val finishCount = AtomicInteger(0)
        val failures = mutableListOf<Throwable>()

        val job = ReceiverCoroutineRunner.launch(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            receiverName = "TestReceiver",
            finish = { finishCount.incrementAndGet() },
            onFailure = { failures += it },
        ) {
            throw CancellationException("receiver work cancelled")
        }

        job.join()

        assertEquals(1, finishCount.get())
        assertTrue(failures.isEmpty())
        assertTrue(job.isCancelled)
    }
}

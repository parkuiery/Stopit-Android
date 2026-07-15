package com.uiery.keep

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepApplicationStartupTest {
    @Test
    fun backgroundStartupLauncherReturnsWhileBothStartupJobsAreStillBlocked() {
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val referrerStarted = CountDownLatch(1)
        val firstPromiseStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            launchKeepApplicationBackgroundWork(
                scope = scope,
                checkInstallReferrer = {
                    referrerStarted.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                },
                runFirstPromiseStartup = {
                    firstPromiseStarted.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                },
            )

            assertTrue(referrerStarted.await(5, TimeUnit.SECONDS))
            assertTrue(firstPromiseStarted.await(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            scope.cancel()
            dispatcher.close()
        }
    }
}

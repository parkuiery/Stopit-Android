package com.uiery.keep.feature.review

import android.app.Activity
import com.uiery.keep.datastore.PreferencesKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class InAppReviewManagerTest {

    private val zone = ZoneId.of("UTC")
    private val nowInstant: Instant = Instant.parse("2026-05-11T14:30:00Z")
    private val nowMs: Long = nowInstant.toEpochMilli()
    private val clock: Clock = Clock.fixed(nowInstant, zone)

    private fun mockActivity(): Activity = Mockito.mock(Activity::class.java)

    @Test
    fun successPathWritesCooldownAndEmitsShown() = runBlocking {
        val launcher = FakeReviewLauncher(nextResult = ReviewLaunchResult.Success)
        val analytics = RecordingKeepAnalytics()
        val dataStore = FakeDataStore()
        val manager = InAppReviewManager(launcher, analytics, dataStore, clock)

        manager.launchIfReady(mockActivity())

        assertEquals(1, launcher.launchCount)
        assertEquals(listOf(AnalyticsEventRecord.Shown), analytics.events)
        assertEquals(nowMs, dataStore.snapshot()[PreferencesKey.LAST_REVIEW_PROMPT_AT_MS])
    }

    @Test
    fun failurePathEmitsFailedAndDoesNotWriteCooldown() = runBlocking {
        val launcher = FakeReviewLauncher(nextResult = ReviewLaunchResult.Failure("no_play_services"))
        val analytics = RecordingKeepAnalytics()
        val dataStore = FakeDataStore()
        val manager = InAppReviewManager(launcher, analytics, dataStore, clock)

        manager.launchIfReady(mockActivity())

        assertEquals(1, launcher.launchCount)
        assertEquals(listOf(AnalyticsEventRecord.Failed("no_play_services")), analytics.events)
        assertNull(dataStore.snapshot()[PreferencesKey.LAST_REVIEW_PROMPT_AT_MS])
    }

    @Test
    fun reentrantCallWhileInFlightShortCircuits() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val launcher = GatedLauncher(gate)
        val analytics = RecordingKeepAnalytics()
        val dataStore = FakeDataStore()
        val manager = InAppReviewManager(launcher, analytics, dataStore, clock)

        val first = async(Dispatchers.Default) { manager.launchIfReady(mockActivity()) }
        while (!launcher.entered.value) {
            delay(1)
        }

        manager.launchIfReady(mockActivity())
        assertEquals(0, analytics.events.size)
        assertEquals(1, launcher.callCount)

        gate.complete(Unit)
        first.await()

        assertEquals(listOf(AnalyticsEventRecord.Shown), analytics.events)
        assertEquals(1, launcher.callCount)
        assertEquals(nowMs, dataStore.snapshot()[PreferencesKey.LAST_REVIEW_PROMPT_AT_MS])
    }

    @Test
    fun nullActivityReturnsImmediately() = runBlocking {
        val launcher = FakeReviewLauncher()
        val analytics = RecordingKeepAnalytics()
        val dataStore = FakeDataStore()
        val manager = InAppReviewManager(launcher, analytics, dataStore, clock)

        manager.launchIfReady(null)

        assertEquals(0, launcher.launchCount)
        assertEquals(emptyList<AnalyticsEventRecord>(), analytics.events)
    }
}

private class GatedLauncher(private val gate: CompletableDeferred<Unit>) : ReviewLauncher {
    val entered = MutableStateFlow(false)
    var callCount: Int = 0
        private set

    override suspend fun launch(activity: Activity): ReviewLaunchResult {
        callCount += 1
        entered.value = true
        gate.await()
        return ReviewLaunchResult.Success
    }
}

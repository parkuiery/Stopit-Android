package com.uiery.keep.analytics

import com.uiery.keep.data.firstpromise.FirstPromiseCoreActionKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstCoreActionDeliveryCoordinatorTest {
    @Test
    fun pendingDurableFirstReservationForcesRepeatAndSuppressesFeedback() = runBlocking {
        val marker = FakeFirstCoreActionMarker(hasTracked = false)
        val coordinator = FirstCoreActionDeliveryCoordinator(
            reservationStore = { true },
            marker = marker,
        )

        val decision = coordinator.decide(nowMillis = 10_000L)

        assertEquals(FirstPromiseCoreActionKind.Repeat, decision.kind)
        assertEquals(false, decision.showFirstCoreActionFeedback)
    }

    @Test
    fun reconciliationMarksLegacyStateAfterDurableFirstDelivery() = runBlocking {
        val marker = FakeFirstCoreActionMarker(hasTracked = false, firstOpenTimestampMillis = 123L)
        val coordinator = FirstCoreActionDeliveryCoordinator(
            reservationStore = { true },
            marker = marker,
        )

        coordinator.reconcileFirstDelivered(nowMillis = 999L)

        assertEquals(true, marker.hasTracked)
        assertEquals(123L, marker.markedTimestamp)
    }
}

private class FakeFirstCoreActionMarker(
    var hasTracked: Boolean,
    private val firstOpenTimestampMillis: Long = 0L,
) : FirstCoreActionMarker {
    var markedTimestamp: Long? = null

    override suspend fun read(nowMillis: Long) = FirstCoreActionMarkerState(
        firstOpenTimestampMillis = firstOpenTimestampMillis,
        hasTracked = hasTracked,
    )

    override suspend fun mark(firstOpenTimestampMillis: Long) {
        hasTracked = true
        markedTimestamp = firstOpenTimestampMillis
    }
}

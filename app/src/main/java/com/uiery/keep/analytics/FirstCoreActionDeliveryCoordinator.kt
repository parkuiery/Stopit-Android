package com.uiery.keep.analytics

import com.uiery.keep.data.firstpromise.FirstPromiseRepository
import com.uiery.keep.data.firstpromise.FirstPromiseCoreActionKind
import com.uiery.keep.datastore.BlockingStateStore
import javax.inject.Inject
import javax.inject.Singleton

data class FirstCoreActionMarkerState(
    val firstOpenTimestampMillis: Long,
    val hasTracked: Boolean,
)

interface FirstCoreActionMarker {
    suspend fun read(nowMillis: Long): FirstCoreActionMarkerState
    suspend fun mark(firstOpenTimestampMillis: Long)
}

fun interface FirstCoreActionReservationStore {
    suspend fun hasFirstReservation(): Boolean
}

data class FirstCoreActionDecision(
    val kind: FirstPromiseCoreActionKind,
    val firstOpenTimestampMillis: Long,
    val showFirstCoreActionFeedback: Boolean,
)

@Singleton
class FirstCoreActionDeliveryCoordinator {
    private val reservationStore: FirstCoreActionReservationStore
    private val marker: FirstCoreActionMarker

    @Inject
    constructor(
        attributionStore: FirstPromiseRepository,
        blockingStateStore: BlockingStateStore,
    ) : this(
        reservationStore = FirstCoreActionReservationStore {
            attributionStore.hasFirstCoreActionReservation()
        },
        marker = BlockingStateFirstCoreActionMarker(blockingStateStore),
    )

    internal constructor(
        reservationStore: FirstCoreActionReservationStore,
        marker: FirstCoreActionMarker,
    ) {
        this.reservationStore = reservationStore
        this.marker = marker
    }

    suspend fun decide(nowMillis: Long): FirstCoreActionDecision {
        val state = marker.read(nowMillis)
        val repeat = state.hasTracked || reservationStore.hasFirstReservation()
        return FirstCoreActionDecision(
            kind = if (repeat) FirstPromiseCoreActionKind.Repeat else FirstPromiseCoreActionKind.First,
            firstOpenTimestampMillis = state.firstOpenTimestampMillis,
            showFirstCoreActionFeedback = !repeat,
        )
    }

    suspend fun markDirectFirstDelivered(firstOpenTimestampMillis: Long) {
        marker.mark(firstOpenTimestampMillis)
    }

    suspend fun reconcileFirstDelivered(nowMillis: Long) {
        val state = marker.read(nowMillis)
        if (!state.hasTracked) marker.mark(state.firstOpenTimestampMillis)
    }
}

private class BlockingStateFirstCoreActionMarker(
    private val store: BlockingStateStore,
) : FirstCoreActionMarker {
    override suspend fun read(nowMillis: Long): FirstCoreActionMarkerState {
        val state = store.readFirstCoreActionState(nowMillis)
        return FirstCoreActionMarkerState(
            firstOpenTimestampMillis = state.firstOpenTimestampMillis,
            hasTracked = state.hasTrackedFirstCoreAction,
        )
    }

    override suspend fun mark(firstOpenTimestampMillis: Long) {
        store.markFirstCoreActionTracked(firstOpenTimestampMillis)
    }
}

package com.uiery.keep.analytics

import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PendingFirstLockConfiguredDelivery
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class FirstLockConfiguredDeliveryCoordinator @Inject constructor(
    private val blockingStateStore: BlockingStateStore,
    private val analytics: KeepAnalytics,
) {
    private val deliveryMutex = Mutex()

    suspend fun reserveIfNeeded(
        source: String,
        selectedAppCount: Int?,
    ): Boolean = deliveryMutex.withLock {
        blockingStateStore.reserveFirstLockConfiguredDelivery(source, selectedAppCount)
    }

    suspend fun trackIfNeeded(
        source: String,
        selectedAppCount: Int?,
    ): Boolean = deliveryMutex.withLock {
        val didReserve = blockingStateStore.reserveFirstLockConfiguredDelivery(source, selectedAppCount)
        deliverPendingLocked()
        didReserve
    }

    suspend fun deliverPending(): Boolean = deliveryMutex.withLock {
        deliverPendingLocked()
    }

    suspend fun releasePending(
        source: String,
        selectedAppCount: Int?,
    ): Boolean = deliveryMutex.withLock {
        blockingStateStore.releaseFirstLockConfiguredDelivery(
            PendingFirstLockConfiguredDelivery(source, selectedAppCount),
        )
    }

    private suspend fun deliverPendingLocked(): Boolean {
        val pending = blockingStateStore.readPendingFirstLockConfiguredDelivery() ?: return false
        return try {
            analytics.trackFirstLockConfigured(
                source = pending.source,
                selectedAppCount = pending.selectedAppCount,
            )
            blockingStateStore.markFirstLockConfiguredDeliveryCompleted(pending)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
    }
}

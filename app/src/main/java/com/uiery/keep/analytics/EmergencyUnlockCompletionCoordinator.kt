package com.uiery.keep.analytics

import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PendingEmergencyUnlockCompletion
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Delivers `emergency_unlock_completed` when the unlock window actually ends.
 *
 * Previously the event was logged next to `emergency_unlock_used` at grant time, so the two
 * were byte-for-byte identical and "completion rate" actually measured grant rate (#1167).
 * Completion is observed by [com.uiery.keep.service.KeepAccessibilityService], which does not
 * know the grant-time payload and may be restarted while a window is open -- hence the
 * reserve-then-deliver split, mirroring [FirstLockConfiguredDeliveryCoordinator].
 *
 * Delivery is best-effort but never duplicated: the reservation is removed only when the
 * analytics call has already returned, and only if it still matches what was read.
 */
@Singleton
class EmergencyUnlockCompletionCoordinator
    @Inject
    constructor(
        private val blockingStateStore: BlockingStateStore,
        private val analytics: KeepAnalytics,
    ) {
        private val deliveryMutex = Mutex()

        /** Records the grant-time payload so the completion can be reported later. */
        suspend fun reserve(
            reason: String,
            durationMinutes: Int,
            remainingUnlocks: Int,
        ) = deliveryMutex.withLock {
            blockingStateStore.reserveEmergencyUnlockCompletion(
                reason = reason,
                durationMinutes = durationMinutes,
                remainingUnlocks = remainingUnlocks,
            )
        }

        /**
         * Reports the completion of the window that has just ended.
         *
         * A no-op when nothing is reserved, which is what makes this safe to call from every
         * state-clearing path without coordinating between them.
         *
         * @return true when an event was delivered.
         */
        suspend fun deliverPending(): Boolean = deliveryMutex.withLock {
            val pending = blockingStateStore.readPendingEmergencyUnlockCompletion() ?: return@withLock false
            return@withLock try {
                analytics.trackEmergencyUnlockCompleted(
                    reason = pending.reason,
                    durationMinutes = pending.durationMinutes,
                    remainingUnlocks = pending.remainingUnlocks,
                )
                blockingStateStore.markEmergencyUnlockCompletionDelivered(pending)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Analytics must never break unlock teardown. The reservation survives, so the
                // next teardown pass can retry.
                false
            }
        }

        /** Drops the reservation without reporting, for a grant that never took effect. */
        suspend fun releasePending(pending: PendingEmergencyUnlockCompletion): Boolean =
            deliveryMutex.withLock {
                blockingStateStore.markEmergencyUnlockCompletionDelivered(pending)
            }
    }

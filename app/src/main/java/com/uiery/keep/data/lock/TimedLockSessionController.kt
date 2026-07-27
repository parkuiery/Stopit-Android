package com.uiery.keep.data.lock

import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.FirstLockConfiguredDeliveryCoordinator
import com.uiery.keep.appselection.BlockExemptPackagePolicy
import com.uiery.keep.appselection.BlockExemptPackageProvider
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.datastore.TimedLockSessionOwnership
import com.uiery.keep.datastore.TimedLockSessionSnapshot
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface TimedLockStartOrigin {
    data class Home(val scheduleType: TimedLockHomeScheduleType) : TimedLockStartOrigin
    data object FirstPromisePractice : TimedLockStartOrigin
}

enum class TimedLockHomeScheduleType(val analyticsValue: String) {
    Countdown(AnalyticsScheduleType.COUNTDOWN),
    Timer(AnalyticsScheduleType.TIMER),
}

sealed interface TimedLockStartResult {
    data class Started(
        val encodedDeadline: String,
        val firstLockConfigured: Boolean,
        internal val ownership: TimedLockSessionOwnership? = null,
        internal val analyticsScheduleType: String? = null,
        internal val analyticsDurationMinutes: Long = 0L,
        internal val firstLockSelectedAppCount: Int? = null,
    ) : TimedLockStartResult

    data object EmptyApps : TimedLockStartResult
    data object InvalidDuration : TimedLockStartResult
    data object AlreadyActive : TimedLockStartResult
}

@Singleton
class TimedLockSessionController @Inject constructor(
    private val blockingStateStore: BlockingStateStore,
    private val analytics: KeepAnalytics,
    private val clock: Clock,
    private val blockExemptPackageProvider: BlockExemptPackageProvider = BlockExemptPackageProvider.None,
    private val firstLockDelivery: FirstLockConfiguredDeliveryCoordinator =
        FirstLockConfiguredDeliveryCoordinator(blockingStateStore, analytics),
) : TimedLockStarter {
    private val startMutex = Mutex()

    override suspend fun start(
        packages: Set<String>,
        durationMinutes: Long,
        origin: TimedLockStartOrigin,
        targetDeadline: Instant?,
        hasWebTargets: Boolean,
    ): TimedLockStartResult = startMutex.withLock {
        // Filter before the empty check. The store drops exempt packages on write, so checking the
        // raw input would let an all-exempt request persist an empty set and still report Started —
        // a full lock screen and countdown that blocks nothing. A web-only lock is still valid.
        val blockablePackages = BlockExemptPackagePolicy.filterBlockable(
            packages = packages,
            exemptPackages = blockExemptPackageProvider.exemptPackages().all,
        )
        if (blockablePackages.isEmpty() && !hasWebTargets) return TimedLockStartResult.EmptyApps
        if (targetDeadline == null && durationMinutes <= 0L) return TimedLockStartResult.InvalidDuration
        val now = clock.instant()
        if (ManualLockTimePolicy.isActiveAt(blockingStateStore.readLockTime(), now, clock.zone)) {
            return TimedLockStartResult.AlreadyActive
        }

        val deadline = targetDeadline ?: now.plusSeconds(durationMinutes * 60L)
        if (!deadline.isAfter(now)) return TimedLockStartResult.InvalidDuration
        val encodedDeadline = ManualLockTimePolicy.encodeDeadline(deadline)
        val ownership = blockingStateStore.startTimedLockSession(
            packages = blockablePackages,
            startTimeMillis = now.toEpochMilli(),
            encodedDeadline = encodedDeadline,
        )

        val scheduleType = when (origin) {
            is TimedLockStartOrigin.Home -> origin.scheduleType.analyticsValue
            TimedLockStartOrigin.FirstPromisePractice -> AnalyticsScheduleType.COUNTDOWN
        }
        val firstLockConfigured = origin is TimedLockStartOrigin.Home &&
            firstLockDelivery.reserveIfNeeded(
                source = AnalyticsSource.HOME_TIMER,
                selectedAppCount = blockablePackages.size,
            )
        TimedLockStartResult.Started(
            encodedDeadline = encodedDeadline,
            firstLockConfigured = firstLockConfigured,
            ownership = ownership,
            analyticsScheduleType = scheduleType,
            analyticsDurationMinutes = durationMinutes,
            // Must match the count reserved above, or rollback cannot release the pending delivery.
            firstLockSelectedAppCount = blockablePackages.size.takeIf { firstLockConfigured },
        )
    }

    override suspend fun commit(started: TimedLockStartResult.Started) {
        val scheduleType = started.analyticsScheduleType ?: return
        if (started.firstLockSelectedAppCount != null) {
            firstLockDelivery.deliverPending()
        }
        trackBestEffort {
            analytics.trackLockScheduled(scheduleType, started.analyticsDurationMinutes)
        }
        trackBestEffort {
            analytics.trackLockSessionStart(source = AnalyticsSource.HOME_TIMER, isRoutine = false)
        }
    }

    override suspend fun rollback(started: TimedLockStartResult.Started): Boolean = startMutex.withLock {
        val ownership = started.ownership ?: TimedLockSessionOwnership(
            encodedDeadline = started.encodedDeadline,
            previous = TimedLockSessionSnapshot(null, null, null),
        )
        val rolledBack = blockingStateStore.rollbackTimedLockSession(ownership)
        if (rolledBack) {
            started.firstLockSelectedAppCount?.let { selectedAppCount ->
                firstLockDelivery.releasePending(
                    source = AnalyticsSource.HOME_TIMER,
                    selectedAppCount = selectedAppCount,
                )
            }
        }
        rolledBack
    }

    private inline fun trackBestEffort(block: () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Timed-lock persistence is authoritative; analytics remains best effort.
        }
    }
}

interface TimedLockStarter {
    suspend fun start(
        packages: Set<String>,
        durationMinutes: Long,
        origin: TimedLockStartOrigin,
        targetDeadline: Instant? = null,
        hasWebTargets: Boolean = false,
    ): TimedLockStartResult

    suspend fun commit(started: TimedLockStartResult.Started)
    suspend fun rollback(started: TimedLockStartResult.Started): Boolean
}

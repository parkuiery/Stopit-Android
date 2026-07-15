package com.uiery.keep.data.lock

import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
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
) : TimedLockStarter {
    private val startMutex = Mutex()

    override suspend fun start(
        packages: Set<String>,
        durationMinutes: Long,
        origin: TimedLockStartOrigin,
        targetDeadline: Instant?,
    ): TimedLockStartResult = startMutex.withLock {
        if (packages.isEmpty()) return TimedLockStartResult.EmptyApps
        if (targetDeadline == null && durationMinutes <= 0L) return TimedLockStartResult.InvalidDuration
        val now = clock.instant()
        if (ManualLockTimePolicy.isActiveAt(blockingStateStore.readLockTime(), now, clock.zone)) {
            return TimedLockStartResult.AlreadyActive
        }

        val deadline = targetDeadline ?: now.plusSeconds(durationMinutes * 60L)
        if (!deadline.isAfter(now)) return TimedLockStartResult.InvalidDuration
        val encodedDeadline = ManualLockTimePolicy.encodeDeadline(deadline)
        blockingStateStore.startTimedLockSession(
            packages = packages,
            startTimeMillis = now.toEpochMilli(),
            encodedDeadline = encodedDeadline,
        )

        val scheduleType = when (origin) {
            is TimedLockStartOrigin.Home -> origin.scheduleType.analyticsValue
            TimedLockStartOrigin.FirstPromisePractice -> AnalyticsScheduleType.COUNTDOWN
        }
        val firstLockConfigured = origin is TimedLockStartOrigin.Home &&
            blockingStateStore.markFirstLockConfiguredIfNeeded()
        if (firstLockConfigured) {
            runCatching {
                analytics.trackFirstLockConfigured(
                    source = AnalyticsSource.HOME_TIMER,
                    selectedAppCount = packages.size,
                )
            }
        }
        runCatching { analytics.trackLockScheduled(scheduleType, durationMinutes) }
        runCatching { analytics.trackLockSessionStart(source = AnalyticsSource.HOME_TIMER, isRoutine = false) }
        TimedLockStartResult.Started(encodedDeadline, firstLockConfigured)
    }

    override suspend fun rollback(started: TimedLockStartResult.Started): Boolean = startMutex.withLock {
        blockingStateStore.rollbackTimedLockSession(started.encodedDeadline)
    }
}

interface TimedLockStarter {
    suspend fun start(
        packages: Set<String>,
        durationMinutes: Long,
        origin: TimedLockStartOrigin,
        targetDeadline: Instant? = null,
    ): TimedLockStartResult

    suspend fun rollback(started: TimedLockStartResult.Started): Boolean
}

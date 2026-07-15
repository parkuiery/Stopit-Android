package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.data.lock.TimedLockStartOrigin
import com.uiery.keep.data.lock.TimedLockStartResult
import com.uiery.keep.data.lock.TimedLockStarter
import com.uiery.keep.datastore.FirstPromisePracticeStore
import com.uiery.keep.datastore.FirstPromisePracticeToken
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromisePracticeOutcome
import com.uiery.keep.feature.review.AccessibilityChecker
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface FirstPromisePracticeStartResult {
    data object Started : FirstPromisePracticeStartResult
    data object RoutineDisabled : FirstPromisePracticeStartResult
    data object AccessibilityRequired : FirstPromisePracticeStartResult
    data object ActiveTimedLock : FirstPromisePracticeStartResult
    data object Failed : FirstPromisePracticeStartResult
}

@Singleton
class FirstPromisePracticeController @Inject constructor(
    private val accessibilityChecker: AccessibilityChecker,
    private val timedLockStarter: TimedLockStarter,
    private val practiceStore: FirstPromisePracticeStore,
    private val analytics: KeepAnalytics,
    private val clock: Clock,
) {
    private val actionMutex = Mutex()
    private var skippedTracked = false

    suspend fun start(
        draft: FirstPromiseDraft,
        routineEnabled: Boolean,
    ): FirstPromisePracticeStartResult = actionMutex.withLock {
        if (!routineEnabled) return FirstPromisePracticeStartResult.RoutineDisabled
        if (!accessibilityChecker.isEnabled()) {
            trackStartFailed()
            return FirstPromisePracticeStartResult.AccessibilityRequired
        }

        val startResult = runCatching {
            timedLockStarter.start(
                packages = setOf(draft.packageName),
                durationMinutes = PRACTICE_DURATION_MINUTES,
                origin = TimedLockStartOrigin.FirstPromisePractice,
            )
        }.getOrElse {
            trackStartFailed()
            return FirstPromisePracticeStartResult.Failed
        }
        if (startResult !is TimedLockStartResult.Started) {
            trackStartFailed()
            return if (startResult == TimedLockStartResult.AlreadyActive) {
                FirstPromisePracticeStartResult.ActiveTimedLock
            } else {
                FirstPromisePracticeStartResult.Failed
            }
        }

        val now = clock.millis()
        return runCatching {
            practiceStore.saveToken(
                FirstPromisePracticeToken(
                    draftId = draft.draftId,
                    startedAtMillis = now,
                    expiresAtMillis = now + PRACTICE_DURATION_MINUTES * 60_000L,
                ),
            )
            analytics.trackFirstPromisePracticeOutcome(FirstPromisePracticeOutcome.Started)
            FirstPromisePracticeStartResult.Started
        }.getOrElse {
            trackStartFailed()
            FirstPromisePracticeStartResult.Failed
        }
    }

    suspend fun skip() = actionMutex.withLock {
        if (!skippedTracked) {
            skippedTracked = true
            analytics.trackFirstPromisePracticeOutcome(FirstPromisePracticeOutcome.Skipped)
        }
    }

    private fun trackStartFailed() {
        analytics.trackFirstPromisePracticeOutcome(FirstPromisePracticeOutcome.StartFailed)
    }

    private companion object {
        const val PRACTICE_DURATION_MINUTES = 10L
    }
}

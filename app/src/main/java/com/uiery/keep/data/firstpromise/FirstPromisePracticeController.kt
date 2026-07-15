package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.data.lock.TimedLockStartOrigin
import com.uiery.keep.data.lock.TimedLockStartResult
import com.uiery.keep.data.lock.TimedLockStarter
import com.uiery.keep.datastore.FirstPromisePracticeStateStore
import com.uiery.keep.datastore.FirstPromisePracticeDecision
import com.uiery.keep.datastore.FirstPromisePracticeToken
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromisePracticeOutcome
import com.uiery.keep.feature.review.AccessibilityChecker
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    private val practiceStore: FirstPromisePracticeStateStore,
    private val analytics: KeepAnalytics,
    private val clock: Clock,
) {
    private val actionMutex = Mutex()

    suspend fun start(
        draft: FirstPromiseDraft,
        routineEnabled: Boolean,
    ): FirstPromisePracticeStartResult = actionMutex.withLock {
        if (!routineEnabled) return FirstPromisePracticeStartResult.RoutineDisabled
        if (!accessibilityChecker.isEnabled()) {
            trackOutcomeSafely(FirstPromisePracticeOutcome.StartFailed)
            return FirstPromisePracticeStartResult.AccessibilityRequired
        }

        val startResult = try {
            timedLockStarter.start(
                packages = setOf(draft.packageName),
                durationMinutes = PRACTICE_DURATION_MINUTES,
                origin = TimedLockStartOrigin.FirstPromisePractice,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            trackOutcomeSafely(FirstPromisePracticeOutcome.StartFailed)
            return FirstPromisePracticeStartResult.Failed
        }
        if (startResult !is TimedLockStartResult.Started) {
            trackOutcomeSafely(FirstPromisePracticeOutcome.StartFailed)
            return if (startResult == TimedLockStartResult.AlreadyActive) {
                FirstPromisePracticeStartResult.ActiveTimedLock
            } else {
                FirstPromisePracticeStartResult.Failed
            }
        }

        val now = clock.millis()
        try {
            practiceStore.saveStarted(
                FirstPromisePracticeToken(
                    draftId = draft.draftId,
                    startedAtMillis = now,
                    expiresAtMillis = now + PRACTICE_DURATION_MINUTES * 60_000L,
                ),
            )
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                var decisionReadSucceeded = false
                val decision = try {
                    practiceStore.readDecision(draft.draftId).also {
                        decisionReadSucceeded = true
                    }
                } catch (failure: Throwable) {
                    cancellation.addSuppressed(failure)
                    null
                }
                if (decisionReadSucceeded && decision != FirstPromisePracticeDecision.Started) {
                    try {
                        timedLockStarter.rollback(startResult)
                    } catch (failure: Throwable) {
                        cancellation.addSuppressed(failure)
                    }
                }
            }
            throw cancellation
        } catch (_: Throwable) {
            withContext(NonCancellable) {
                try {
                    timedLockStarter.rollback(startResult)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // The durable decision was not committed; keep the start failure retryable.
                }
            }
            trackOutcomeSafely(FirstPromisePracticeOutcome.StartFailed)
            return FirstPromisePracticeStartResult.Failed
        }
        timedLockStarter.commit(startResult)
        trackOutcomeSafely(FirstPromisePracticeOutcome.Started)
        FirstPromisePracticeStartResult.Started
    }

    suspend fun skip(draftId: String, routineEnabled: Boolean) = actionMutex.withLock {
        if (!routineEnabled) return@withLock
        if (practiceStore.recordSkippedIfAbsent(draftId)) {
            trackOutcomeSafely(FirstPromisePracticeOutcome.Skipped)
        }
    }

    private fun trackOutcomeSafely(outcome: FirstPromisePracticeOutcome) {
        try {
            analytics.trackFirstPromisePracticeOutcome(outcome)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Analytics is best effort after the durable decision boundary.
        }
    }

    private companion object {
        const val PRACTICE_DURATION_MINUTES = 10L
    }
}

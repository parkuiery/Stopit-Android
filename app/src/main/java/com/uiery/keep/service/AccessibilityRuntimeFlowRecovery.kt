package com.uiery.keep.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

internal enum class AccessibilityRuntimeFlowSource(
    val debugName: String,
) {
    BlockingState("blocking_state"),
    Routines("routines"),
    GoalLocks("goal_locks"),
    ParentMode("parent_mode"),
}

internal data class AccessibilityRuntimeFlowRecoveryEvent(
    val source: AccessibilityRuntimeFlowSource,
    val attempt: Long,
    val retryDelayMillis: Long,
    val errorType: String,
)

internal object AccessibilityRuntimeFlowRecoveryPolicy {
    private const val BASE_RETRY_DELAY_MS = 1_000L
    private const val MAX_RETRY_DELAY_MS = 30_000L

    fun retryDelayMillis(attempt: Long): Long =
        (BASE_RETRY_DELAY_MS * (attempt + 1)).coerceAtMost(MAX_RETRY_DELAY_MS)

    fun eventFor(
        source: AccessibilityRuntimeFlowSource,
        cause: Throwable,
        attempt: Long,
    ): AccessibilityRuntimeFlowRecoveryEvent = AccessibilityRuntimeFlowRecoveryEvent(
        source = source,
        attempt = attempt,
        retryDelayMillis = retryDelayMillis(attempt),
        errorType = cause::class.simpleName ?: "Throwable",
    )
}

internal fun <T> Flow<T>.withAccessibilityRuntimeRecovery(
    source: AccessibilityRuntimeFlowSource,
    onRecoveryEvent: (AccessibilityRuntimeFlowRecoveryEvent) -> Unit,
    delayMillis: suspend (Long) -> Unit = { delay(it) },
): Flow<T> = retryWhen { cause, attempt ->
    val event = AccessibilityRuntimeFlowRecoveryPolicy.eventFor(
        source = source,
        cause = cause,
        attempt = attempt,
    )
    onRecoveryEvent(event)
    delayMillis(event.retryDelayMillis)
    true
}

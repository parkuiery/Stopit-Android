package com.uiery.keep.service

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.uiery.keep.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class FcmTokenPersistenceFailure(
    val cause: Throwable,
) {
    val causeClassName: String = cause::class.java.name
}

internal fun interface FcmTokenPersistenceFailureReporter {
    fun record(failure: FcmTokenPersistenceFailure)
}

internal class FcmTokenPersistenceException(
    failure: FcmTokenPersistenceFailure,
) : RuntimeException("FCM token persistence failed: ${failure.causeClassName}")

internal object CrashlyticsFcmTokenPersistenceFailureReporter : FcmTokenPersistenceFailureReporter {
    override fun record(failure: FcmTokenPersistenceFailure) {
        AppLogger.debug(
            tag = TAG,
            message = "FCM token persistence failed: ${failure.causeClassName}",
        )
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("fcm_token_persistence_failure", true)
            setCustomKey("fcm_token_persistence_cause", failure.causeClassName)
            recordException(FcmTokenPersistenceException(failure))
        }
    }

    private const val TAG = "KeepMessagingService"
}

internal object FcmTokenPersistenceRunner {
    private const val DefaultMaxAttempts = 3
    private const val DefaultRetryDelayMillis = 1_000L

    fun launch(
        work: suspend () -> Unit,
    ): Job = launch(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        failureReporter = CrashlyticsFcmTokenPersistenceFailureReporter,
        maxAttempts = DefaultMaxAttempts,
        retryDelayMillis = DefaultRetryDelayMillis,
        work = work,
    )

    fun launchDeviceTokenPersistence(
        token: String,
        saveDeviceToken: suspend (String) -> Unit,
    ): Job = launchDeviceTokenPersistence(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        failureReporter = CrashlyticsFcmTokenPersistenceFailureReporter,
        maxAttempts = DefaultMaxAttempts,
        retryDelayMillis = DefaultRetryDelayMillis,
        token = token,
        saveDeviceToken = saveDeviceToken,
    )

    fun launchDeviceTokenPersistence(
        scope: CoroutineScope,
        failureReporter: FcmTokenPersistenceFailureReporter,
        token: String,
        maxAttempts: Int = DefaultMaxAttempts,
        retryDelayMillis: Long = DefaultRetryDelayMillis,
        saveDeviceToken: suspend (String) -> Unit,
    ): Job = launch(
        scope = scope,
        failureReporter = failureReporter,
        maxAttempts = maxAttempts,
        retryDelayMillis = retryDelayMillis,
    ) {
        saveDeviceToken(token)
    }

    fun launch(
        scope: CoroutineScope,
        failureReporter: FcmTokenPersistenceFailureReporter,
        maxAttempts: Int = DefaultMaxAttempts,
        retryDelayMillis: Long = DefaultRetryDelayMillis,
        work: suspend () -> Unit,
    ): Job = scope.launch {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        repeat(maxAttempts) { attemptIndex ->
            try {
                work()
                return@launch
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val isFinalAttempt = attemptIndex == maxAttempts - 1
                if (isFinalAttempt) {
                    failureReporter.record(FcmTokenPersistenceFailure(exception))
                } else if (retryDelayMillis > 0) {
                    delay(retryDelayMillis)
                }
            }
        }
    }
}

package com.uiery.keep.receiver

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.uiery.keep.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal data class ReceiverFailure(
    val receiverName: String,
    val cause: Throwable,
)

internal fun interface ReceiverFailureReporter {
    fun record(failure: ReceiverFailure)
}

internal class ReceiverCoroutineException(
    receiverName: String,
    cause: Throwable,
) : RuntimeException("Receiver async work failed in $receiverName", cause)

internal object CrashlyticsReceiverFailureReporter : ReceiverFailureReporter {
    override fun record(failure: ReceiverFailure) {
        AppLogger.debug(failure.receiverName, "Receiver async work failed", failure.cause)
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("receiver_name", failure.receiverName)
            recordException(
                ReceiverCoroutineException(
                    receiverName = failure.receiverName,
                    cause = failure.cause,
                ),
            )
        }
    }
}

internal object ReceiverCoroutineRunner {

    fun launch(
        receiverName: String,
        finish: () -> Unit,
        work: suspend () -> Unit,
    ): Job = launch(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        receiverName = receiverName,
        finish = finish,
        failureReporter = CrashlyticsReceiverFailureReporter,
        work = work,
    )

    fun launch(
        scope: CoroutineScope,
        receiverName: String,
        finish: () -> Unit,
        failureReporter: ReceiverFailureReporter,
        work: suspend () -> Unit,
    ): Job = launch(
        scope = scope,
        receiverName = receiverName,
        finish = finish,
        onFailure = { throwable ->
            failureReporter.record(
                ReceiverFailure(
                    receiverName = receiverName,
                    cause = throwable,
                ),
            )
        },
        work = work,
    )

    fun launch(
        scope: CoroutineScope,
        receiverName: String,
        finish: () -> Unit,
        onFailure: (Throwable) -> Unit,
        work: suspend () -> Unit,
    ): Job = scope.launch {
        try {
            work()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            onFailure(exception)
        } finally {
            finish()
        }
    }
}

package com.uiery.keep.receiver

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object ReceiverCoroutineRunner {

    fun launch(
        receiverName: String,
        finish: () -> Unit,
        work: suspend () -> Unit,
    ): Job = launch(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        receiverName = receiverName,
        finish = finish,
        onFailure = { throwable ->
            Log.e(receiverName, "Receiver async work failed", throwable)
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

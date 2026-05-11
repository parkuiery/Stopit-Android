package com.uiery.keep.feature.review

import android.app.Activity
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class PlayReviewLauncher @Inject constructor(
    @ApplicationContext appContext: Context,
) : ReviewLauncher {

    private val manager = ReviewManagerFactory.create(appContext)

    override suspend fun launch(activity: Activity): ReviewLaunchResult = try {
        val info = manager.requestReviewFlow().awaitResult()
        manager.launchReviewFlow(activity, info).awaitResult()
        ReviewLaunchResult.Success
    } catch (t: Throwable) {
        ReviewLaunchResult.Failure(t.message ?: t.javaClass.simpleName)
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { e -> cont.resumeWithException(e) }
    addOnCanceledListener { cont.resumeWithException(RuntimeException("Task canceled")) }
}

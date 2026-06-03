package com.uiery.keep.feature.review

import android.app.Activity
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.ReviewPromptStateStore
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppReviewManager @Inject constructor(
    private val launcher: ReviewLauncher,
    private val analytics: KeepAnalytics,
    private val reviewPromptStateStore: ReviewPromptStateStore,
    private val clock: Clock,
) {

    private val inFlight = AtomicBoolean(false)

    suspend fun launchIfReady(activity: Activity?): Boolean {
        if (activity == null) return false
        if (!inFlight.compareAndSet(false, true)) return false
        return try {
            when (val result = launcher.launch(activity)) {
                is ReviewLaunchResult.Success -> {
                    reviewPromptStateStore.recordPromptShown(clock.millis())
                    analytics.reviewPromptShown()
                    true
                }
                is ReviewLaunchResult.Failure -> {
                    analytics.reviewPromptFailed(result.error)
                    false
                }
            }
        } finally {
            inFlight.set(false)
        }
    }
}

package com.uiery.keep.feature.review

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.PreferencesKey
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppReviewManager @Inject constructor(
    private val launcher: ReviewLauncher,
    private val analytics: KeepAnalytics,
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {

    private val inFlight = AtomicBoolean(false)

    suspend fun launchIfReady(activity: Activity?) {
        if (activity == null) return
        if (!inFlight.compareAndSet(false, true)) return
        try {
            when (val result = launcher.launch(activity)) {
                is ReviewLaunchResult.Success -> {
                    dataStore.edit { it[PreferencesKey.LAST_REVIEW_PROMPT_AT_MS] = clock.millis() }
                    analytics.reviewPromptShown()
                }
                is ReviewLaunchResult.Failure -> {
                    analytics.reviewPromptFailed(result.error)
                }
            }
        } finally {
            inFlight.set(false)
        }
    }
}

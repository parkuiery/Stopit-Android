package com.uiery.keep.analytics

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.uiery.keep.BuildConfig
import com.uiery.keep.analytics.amplitude.AmplitudeAnalyticsBackendFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Single composition point for analytics transport. Fans out to Firebase (full
     * catalog) and Amplitude (allowlisted subset, free-tier budget). Both
     * [FirebaseKeepAnalytics] and the ad tracker resolve this one singleton, so no
     * `trackXxx` call site needs to know a second backend exists.
     */
    @Provides
    @Singleton
    fun provideAnalyticsBackend(
        @ApplicationContext context: Context,
    ): AnalyticsBackend {
        val firebaseBackend = AnalyticsBackendFactory.create {
            FirebaseAnalyticsBackend(Firebase.analytics)
        }
        val amplitudeBackend = AmplitudeAnalyticsBackendFactory.create(
            context = context,
            apiKey = BuildConfig.AMPLITUDE_API_KEY,
        )
        return CompositeAnalyticsBackend(listOf(firebaseBackend, amplitudeBackend))
    }
}

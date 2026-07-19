package com.uiery.keep.feature.review

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
import com.uiery.keep.BuildConfig
import com.uiery.keep.feature.onboarding.experiment.FirebaseOnboardingExperimentConfig
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {

    @Binds
    @Singleton
    abstract fun bindReviewLauncher(impl: PlayReviewLauncher): ReviewLauncher

    @Binds
    @Singleton
    abstract fun bindReviewRemoteConfig(impl: FirebaseReviewRemoteConfig): ReviewRemoteConfig

    @Binds
    @Singleton
    abstract fun bindOnboardingExperimentConfig(impl: FirebaseOnboardingExperimentConfig): OnboardingExperimentConfig

    @Binds
    @Singleton
    abstract fun bindAccessibilityChecker(impl: AndroidAccessibilityChecker): AccessibilityChecker

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()

        @Provides
        @Singleton
        fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig =
            FirebaseRemoteConfigInitializer.initialize(Firebase.remoteConfig)

        @Provides
        @Singleton
        fun provideReviewBuildConfig(): ReviewBuildConfig = ReviewBuildConfig(
            isDebug = BuildConfig.DEBUG,
            flavor = BuildConfig.FLAVOR,
        )
    }
}

private const val FETCH_TIMEOUT_SECONDS = 5L

private val SHARED_REMOTE_CONFIG_DEFAULTS = mapOf<String, Any>(
    "in_app_review_enabled" to true,
    "onboarding_variant" to "control",
)

internal object FirebaseRemoteConfigInitializer {
    fun initialize(remoteConfig: FirebaseRemoteConfig): FirebaseRemoteConfig {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
                .build(),
        )
        remoteConfig.setDefaultsAsync(SHARED_REMOTE_CONFIG_DEFAULTS)
        remoteConfig.fetchAndActivate()
        return remoteConfig
    }
}

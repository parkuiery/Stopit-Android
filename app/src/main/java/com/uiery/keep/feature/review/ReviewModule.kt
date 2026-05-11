package com.uiery.keep.feature.review

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.uiery.keep.BuildConfig
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
    abstract fun bindAccessibilityChecker(impl: AndroidAccessibilityChecker): AccessibilityChecker

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()

        @Provides
        @Singleton
        fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig = Firebase.remoteConfig

        @Provides
        @Singleton
        fun provideReviewBuildConfig(): ReviewBuildConfig = ReviewBuildConfig(
            isDebug = BuildConfig.DEBUG,
            flavor = BuildConfig.FLAVOR,
        )
    }
}

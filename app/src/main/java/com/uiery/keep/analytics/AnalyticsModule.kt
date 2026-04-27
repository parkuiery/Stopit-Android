package com.uiery.keep.analytics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    abstract fun bindAnalyticsBackend(
        impl: FirebaseAnalyticsBackend,
    ): AnalyticsBackend

    @Binds
    abstract fun bindKeepAnalytics(
        impl: FirebaseKeepAnalytics,
    ): KeepAnalytics
}

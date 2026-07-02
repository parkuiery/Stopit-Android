package com.uiery.keep.feature.lockhistory

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LockHistoryModule {

    @Binds
    @Singleton
    abstract fun bindFocusSummaryShareTextProvider(
        impl: AndroidFocusSummaryShareTextProvider,
    ): FocusSummaryShareTextProvider
}

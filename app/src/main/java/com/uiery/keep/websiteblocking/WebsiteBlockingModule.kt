package com.uiery.keep.websiteblocking

import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingLauncher
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingAsserter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WebsiteBlockingModule {
    @Binds
    abstract fun bindRoutineWebsiteBlockingLauncher(
        impl: AndroidRoutineWebsiteBlockingLauncher,
    ): RoutineWebsiteBlockingLauncher

    @Binds
    abstract fun bindWebsiteBlockingAsserter(
        impl: AndroidWebsiteBlockingAsserter,
    ): WebsiteBlockingAsserter
}

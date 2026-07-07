package com.uiery.keep.data.usageinsight

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UsageInsightModule {
    @Binds
    abstract fun bindUsageStatsGateway(impl: AndroidUsageStatsGateway): UsageStatsGateway
}

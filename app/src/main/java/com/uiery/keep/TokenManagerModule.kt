package com.uiery.keep

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.LocalDeviceDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TokenManagerModule {
    @Provides
    @Singleton
    fun provideTokenManager(
        localDeviceDataStore: LocalDeviceDataStore,
        analytics: KeepAnalytics,
    ): DeviceTokenManager {
        return DeviceTokenManager(localDeviceDataStore, analytics)
    }
}

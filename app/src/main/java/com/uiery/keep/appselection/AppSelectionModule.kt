package com.uiery.keep.appselection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppSelectionModule {
    @Binds
    abstract fun bindBlockExemptPackageProvider(
        impl: AndroidBlockExemptPackageProvider,
    ): BlockExemptPackageProvider
}

package com.uiery.keep.data.repeatblock.di

import com.uiery.keep.data.repeatblock.InstalledAppCategoryResolver
import com.uiery.keep.domain.repeatblock.AppCategoryResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepeatBlockModule {
    /**
     * 앱 부류 판단은 앱이 살아 있는 동안 바뀌지 않으므로 한 벌만 두고 결과 캐시를 공유한다.
     */
    @Binds
    @Singleton
    abstract fun bindAppCategoryResolver(
        resolver: InstalledAppCategoryResolver,
    ): AppCategoryResolver
}

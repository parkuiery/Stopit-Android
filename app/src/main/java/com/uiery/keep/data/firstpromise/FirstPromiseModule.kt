package com.uiery.keep.data.firstpromise

import com.uiery.keep.data.lock.TimedLockSessionController
import com.uiery.keep.data.lock.TimedLockStarter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FirstPromiseModule {
    @Binds
    @Singleton
    abstract fun bindFirstPromiseCreator(repository: FirstPromiseRepository): FirstPromiseCreator

    @Binds
    @Singleton
    abstract fun bindFirstPromiseOutboxDispatcher(
        dispatcher: FirstPromiseAnalyticsDispatcher,
    ): FirstPromiseOutboxDispatcher

    @Binds
    @Singleton
    abstract fun bindTimedLockStarter(controller: TimedLockSessionController): TimedLockStarter
}

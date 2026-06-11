package com.uiery.keep.feature.routine

import com.uiery.keep.data.routine.RoomRoutineRepository
import com.uiery.keep.data.routine.RoutineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RoutineModule {
    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: RoomRoutineRepository): RoutineRepository
}

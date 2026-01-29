package com.uiery.keep.database.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.uiery.keep.database.KeepDatabase
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.dao.RoutineDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Singleton
    @Provides
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideKeepDatabase(
        @ApplicationContext context: Context,
    ): KeepDatabase = Room.databaseBuilder(
        context = context,
        klass = KeepDatabase::class.java,
        name = "keep-database"
    )
        .addMigrations(KeepDatabase.MIGRATION_1_2)
        .build()

    @Provides
    @Singleton
    fun provideRoutineDao(db: KeepDatabase): RoutineDao = db.routineDao()

    @Provides
    @Singleton
    fun provideLockHistoryDao(db: KeepDatabase): LockHistoryDao = db.lockHistoryDao()

}
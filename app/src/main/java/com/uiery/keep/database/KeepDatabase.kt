package com.uiery.keep.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.uiery.keep.database.converter.DayOfWeekTypeConverter
import com.uiery.keep.database.converter.ListStringTypeConverter
import com.uiery.keep.database.converter.TimeTypeConverter
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity

@Database(
    entities = [
        RoutineEntity::class
    ],
    version = 1,
)
@TypeConverters(
    value = [
        TimeTypeConverter::class,
        DayOfWeekTypeConverter::class,
        ListStringTypeConverter::class
    ],
)
abstract class KeepDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
}
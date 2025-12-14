package com.uiery.keep.database.converter

import androidx.room.TypeConverter
import kotlinx.datetime.LocalTime

internal class TimeTypeConverter {

    @TypeConverter
    fun fromLocalTime(value: LocalTime): String = value.toString()

    @TypeConverter
    fun toLocalTime(value: String): LocalTime = LocalTime.parse(value)
}
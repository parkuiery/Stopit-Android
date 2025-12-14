package com.uiery.keep.database.converter

import androidx.room.TypeConverter
import java.time.DayOfWeek

class DayOfWeekTypeConverter {
    @TypeConverter
    fun fromDayOfWeekList(days: List<DayOfWeek>?): String? {
        return days?.joinToString(",") { it.name }
    }

    @TypeConverter
    fun toDayOfWeekList(data: String?): List<DayOfWeek>? {
        return data?.split(",")?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
    }
}
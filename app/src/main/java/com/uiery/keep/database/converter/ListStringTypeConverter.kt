package com.uiery.keep.database.converter

import androidx.room.TypeConverter

class ListStringTypeConverter {
    @TypeConverter
    fun fromString(value: String?): List<String>? {
        return value?.split(",")
    }

    @TypeConverter
    fun fromList(list: List<String>?): String? {
        return list?.joinToString(",")
    }
}
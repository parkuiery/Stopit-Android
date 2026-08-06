package com.uiery.keep.database.converter

import androidx.room.TypeConverter

class ListStringTypeConverter {
    /**
     * 빈 문자열은 빈 목록이다. `"".split(",")` 는 빈 항목 하나를 가진 목록을 돌려주므로,
     * 그대로 두면 아무것도 담기지 않은 열이 "대상 한 개"로 읽힌다.
     */
    @TypeConverter
    fun fromString(value: String?): List<String>? {
        return value?.split(",")?.filter { it.isNotBlank() }
    }

    @TypeConverter
    fun fromList(list: List<String>?): String? {
        return list?.joinToString(",")
    }
}
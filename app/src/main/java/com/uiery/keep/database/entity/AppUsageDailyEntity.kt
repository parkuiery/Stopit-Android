package com.uiery.keep.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** 완결일(어제 이전) 단위 앱 사용 집계 캐시. date는 ISO-8601(yyyy-MM-dd). */
@Entity(tableName = "app_usage_daily", primaryKeys = ["date", "package_name"])
data class AppUsageDailyEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "total_usage_millis") val totalUsageMillis: Long,
    @ColumnInfo(name = "launch_count") val launchCount: Int,
    @ColumnInfo(name = "night_usage_millis") val nightUsageMillis: Long,
)

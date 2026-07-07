package com.uiery.keep.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uiery.keep.database.entity.AppUsageDailyEntity

@Dao
interface AppUsageDailyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<AppUsageDailyEntity>)

    @Query("SELECT * FROM app_usage_daily WHERE date >= :fromDate ORDER BY date")
    suspend fun getSince(fromDate: String): List<AppUsageDailyEntity>

    @Query("SELECT MAX(date) FROM app_usage_daily")
    suspend fun latestDate(): String?

    @Query("DELETE FROM app_usage_daily WHERE date < :beforeDate")
    suspend fun deleteBefore(beforeDate: String)
}

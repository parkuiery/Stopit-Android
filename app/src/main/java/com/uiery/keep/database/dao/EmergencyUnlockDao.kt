package com.uiery.keep.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyUnlockDao {
    @Insert
    suspend fun insert(entity: EmergencyUnlockEntity)

    @Query("SELECT * FROM emergency_unlock WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun fetchByDateRange(start: Long, end: Long): Flow<List<EmergencyUnlockEntity>>

    @Query("SELECT COUNT(*) FROM emergency_unlock WHERE timestamp >= :todayStart")
    suspend fun countToday(todayStart: Long): Int

    @Query("SELECT COUNT(*) FROM emergency_unlock WHERE timestamp >= :timestampMillis")
    suspend fun countSince(timestampMillis: Long): Int
}

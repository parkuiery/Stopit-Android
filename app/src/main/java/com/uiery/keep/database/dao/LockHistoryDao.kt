package com.uiery.keep.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uiery.keep.database.entity.LockHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LockHistoryDao {

    @Insert
    suspend fun insert(entity: LockHistoryEntity)

    @Query("SELECT * FROM lock_history WHERE start_timestamp >= :startMillis AND start_timestamp < :endMillis ORDER BY start_timestamp DESC")
    fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>>

    @Query("SELECT * FROM lock_history ORDER BY start_timestamp DESC")
    fun fetchAll(): Flow<List<LockHistoryEntity>>
}

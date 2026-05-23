package com.uiery.keep.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.uiery.keep.database.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM routine")
    fun fetchAll() : Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine")
    fun fetchAllOnce(): List<RoutineEntity>

    @Query("SELECT * FROM routine WHERE id = :id")
    fun fetch(id: Long): RoutineEntity

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(routineEntity: RoutineEntity): Long

    @Query("DELETE FROM routine WHERE id = :id")
    fun deleteById(id: Long)

    @Update
    fun update(routineEntity: RoutineEntity)

    @Query("UPDATE routine SET is_enabled = :isEnabled WHERE id = :id")
    fun updateIsEnabledById(id: Long, isEnabled: Boolean)
}
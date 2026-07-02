package com.uiery.keep.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.uiery.keep.database.entity.GoalLockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalLockDao {
    @Query("SELECT * FROM goal_lock ORDER BY start_date DESC, id DESC")
    fun fetchAll(): Flow<List<GoalLockEntity>>

    @Query("SELECT * FROM goal_lock WHERE id = :id")
    fun fetch(id: Long): GoalLockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(goalLock: GoalLockEntity): Long

    @Update
    fun update(goalLock: GoalLockEntity)
}

package com.uiery.keep.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uiery.keep.database.entity.FirstPromiseEntity

@Dao
interface FirstPromiseDao {
    @Insert
    suspend fun insert(entity: FirstPromiseEntity)

    @Query("SELECT * FROM first_promise WHERE draft_id = :draftId")
    suspend fun findByDraftId(draftId: String): FirstPromiseEntity?

    @Query("SELECT * FROM first_promise WHERE routine_id = :routineId")
    suspend fun findByRoutineId(routineId: Long): FirstPromiseEntity?
}

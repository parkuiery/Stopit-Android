package com.uiery.keep.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uiery.keep.database.entity.FirstPromiseAnalyticsOutboxEntity

@Dao
interface FirstPromiseAnalyticsOutboxDao {
    @Insert
    suspend fun insertAll(entities: List<FirstPromiseAnalyticsOutboxEntity>)

    @Query(
        "SELECT * FROM first_promise_analytics_outbox " +
            "WHERE draft_id = :draftId ORDER BY sequence",
    )
    suspend fun findByDraftId(draftId: String): List<FirstPromiseAnalyticsOutboxEntity>
}

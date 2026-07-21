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

    @Query(
        "SELECT COUNT(*) FROM first_promise_analytics_outbox " +
            "WHERE sequence = 40 AND canonical_event_name = 'first_core_action_completed' " +
            "AND delivery_state != 'quarantined'",
    )
    suspend fun countFirstCoreActionReservations(): Int

    @Query(
        "SELECT COUNT(*) FROM first_promise_analytics_outbox " +
            "WHERE sequence = 40 AND canonical_event_name = 'first_core_action_completed' " +
            "AND delivery_state = 'sent'",
    )
    suspend fun countSentFirstCoreActions(): Int

    @Query(
        "SELECT COUNT(*) FROM first_promise_analytics_outbox " +
            "WHERE draft_id = :draftId AND sequence IN (30, 40) AND delivery_state != 'sent'",
    )
    suspend fun countIncompleteValueEvents(draftId: String): Int

    @Query(
        "SELECT draft_id FROM first_promise_analytics_outbox " +
            "WHERE delivery_state = 'pending' GROUP BY draft_id " +
            "ORDER BY MIN(sequence), draft_id",
    )
    suspend fun findPendingDraftIds(): List<String>

    @Query(
        "SELECT candidate.* FROM first_promise_analytics_outbox AS candidate " +
            "WHERE candidate.draft_id = :draftId AND candidate.delivery_state = 'pending' " +
            "AND NOT EXISTS (SELECT 1 FROM first_promise_analytics_outbox AS predecessor " +
            "WHERE predecessor.draft_id = candidate.draft_id " +
            "AND predecessor.sequence < candidate.sequence " +
            "AND predecessor.delivery_state != 'sent') " +
            "ORDER BY candidate.sequence LIMIT 1",
    )
    suspend fun findNextDeliverable(draftId: String): FirstPromiseAnalyticsOutboxEntity?

    @Query(
        "UPDATE first_promise_analytics_outbox SET delivery_state = 'sent', sent_at_millis = :sentAtMillis " +
            "WHERE draft_id = :draftId AND event_name = :eventName AND delivery_state = 'pending'",
    )
    suspend fun markSent(draftId: String, eventName: String, sentAtMillis: Long): Int

    @Query(
        "UPDATE first_promise_analytics_outbox SET delivery_state = 'quarantined' " +
            "WHERE draft_id = :draftId AND event_name = :eventName AND delivery_state = 'pending'",
    )
    suspend fun quarantine(draftId: String, eventName: String): Int

    @Query(
        "DELETE FROM first_promise_analytics_outbox " +
            "WHERE delivery_state = 'sent' AND sent_at_millis < :cutoffMillis " +
            "AND (sequence NOT IN (10, 20) OR draft_id IN (:creationBarrierReadyDraftIds))",
    )
    suspend fun deleteSentBefore(cutoffMillis: Long, creationBarrierReadyDraftIds: List<String>)

    @Query(
        "SELECT COUNT(*) FROM first_promise_analytics_outbox " +
            "WHERE draft_id = :draftId AND sequence IN (10, 20) AND delivery_state = 'sent'",
    )
    suspend fun countSentCreationEvents(draftId: String): Int

    @Query(
        "SELECT draft_id FROM first_promise_analytics_outbox " +
            "WHERE sequence IN (10, 20) AND delivery_state = 'sent' " +
            "GROUP BY draft_id HAVING COUNT(*) = 2",
    )
    suspend fun findCreationBarrierReadyDraftIds(): List<String>
}

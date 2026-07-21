package com.uiery.keep.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "first_promise_analytics_outbox",
    primaryKeys = ["draft_id", "event_name"],
    indices = [Index(value = ["draft_id", "sequence"])],
)
data class FirstPromiseAnalyticsOutboxEntity(
    @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "event_name") val eventName: String,
    val sequence: Int,
    @ColumnInfo(name = "canonical_event_name") val canonicalEventName: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "occurred_at_millis") val occurredAtMillis: Long,
    @ColumnInfo(name = "delivery_state") val deliveryState: String,
    @ColumnInfo(name = "sent_at_millis") val sentAtMillis: Long? = null,
)

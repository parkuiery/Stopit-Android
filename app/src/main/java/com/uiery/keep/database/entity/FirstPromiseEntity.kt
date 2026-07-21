package com.uiery.keep.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "first_promise",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["routine_id"], unique = true)],
)
data class FirstPromiseEntity(
    @PrimaryKey @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    @ColumnInfo(name = "goal_type") val goalType: String,
    val source: String,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)

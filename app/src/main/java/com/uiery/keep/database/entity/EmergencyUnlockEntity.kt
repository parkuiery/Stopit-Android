package com.uiery.keep.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_unlock")
data class EmergencyUnlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "custom_reason") val customReason: String? = null,
    @ColumnInfo(name = "unlocked_apps") val unlockedApps: List<String>,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
)

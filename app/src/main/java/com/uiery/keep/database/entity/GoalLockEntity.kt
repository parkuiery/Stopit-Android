package com.uiery.keep.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.uiery.keep.feature.goallock.GoalLock
import com.uiery.keep.feature.goallock.GoalLockMode
import com.uiery.keep.feature.goallock.GoalLockStoredStatus
import java.time.DayOfWeek
import java.time.LocalDate

@Entity(tableName = "goal_lock")
data class GoalLockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "goal_name") val goalName: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "lock_mode") val lockMode: String,
    @ColumnInfo(name = "repeat_days") val repeatDays: List<DayOfWeek>? = null,
    @ColumnInfo(name = "start_time") val startTime: String? = null,
    @ColumnInfo(name = "end_time") val endTime: String? = null,
    @ColumnInfo(name = "selected_packages") val selectedPackages: List<String>,
    @ColumnInfo(name = "status") val status: String,
) {
    internal fun toDomain(): GoalLock = GoalLock(
        id = id,
        goalName = goalName,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(endDate),
        lockMode = when (lockMode) {
            LOCK_MODE_ALL_DAY -> GoalLockMode.AllDay
            LOCK_MODE_SCHEDULED -> GoalLockMode.Scheduled(
                repeatDays = requireNotNull(repeatDays).toSet(),
                startTime = java.time.LocalTime.parse(requireNotNull(startTime)),
                endTime = java.time.LocalTime.parse(requireNotNull(endTime)),
            )
            else -> error("Unknown goal lock mode: $lockMode")
        },
        selectedPackages = selectedPackages.toSet(),
        status = when (status) {
            STATUS_ACTIVE -> GoalLockStoredStatus.Active
            STATUS_ENDED_EARLY -> GoalLockStoredStatus.EndedEarly
            else -> error("Unknown goal lock status: $status")
        },
    )

    companion object {
        internal const val LOCK_MODE_ALL_DAY = "all_day"
        internal const val LOCK_MODE_SCHEDULED = "scheduled"
        internal const val STATUS_ACTIVE = "active"
        internal const val STATUS_ENDED_EARLY = "ended_early"

        internal fun fromDomain(goalLock: GoalLock): GoalLockEntity {
            val scheduled = goalLock.lockMode as? GoalLockMode.Scheduled
            return GoalLockEntity(
                id = goalLock.id,
                goalName = goalLock.goalName,
                startDate = goalLock.startDate.toString(),
                endDate = goalLock.endDate.toString(),
                lockMode = when (goalLock.lockMode) {
                    GoalLockMode.AllDay -> LOCK_MODE_ALL_DAY
                    is GoalLockMode.Scheduled -> LOCK_MODE_SCHEDULED
                },
                repeatDays = scheduled?.repeatDays?.toList(),
                startTime = scheduled?.startTime?.toString(),
                endTime = scheduled?.endTime?.toString(),
                selectedPackages = goalLock.selectedPackages.toList(),
                status = when (goalLock.status) {
                    GoalLockStoredStatus.Active -> STATUS_ACTIVE
                    GoalLockStoredStatus.EndedEarly -> STATUS_ENDED_EARLY
                },
            )
        }
    }
}

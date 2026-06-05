package com.uiery.keep.feature.goallock

import com.uiery.keep.analytics.AnalyticsGoalLockDurationDaysBucket
import com.uiery.keep.analytics.AnalyticsGoalLockMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal val GoalLockMode.analyticsLockMode: String
    get() = when (this) {
        GoalLockMode.AllDay -> AnalyticsGoalLockMode.ALL_DAY
        is GoalLockMode.Scheduled -> AnalyticsGoalLockMode.SCHEDULED
    }

internal fun goalLockDurationDaysBucket(
    startDate: LocalDate,
    endDate: LocalDate,
): String = when (ChronoUnit.DAYS.between(startDate, endDate).plus(1).coerceAtLeast(1)) {
    in 1L..6L -> AnalyticsGoalLockDurationDaysBucket.ONE_TO_SIX
    7L -> AnalyticsGoalLockDurationDaysBucket.SEVEN
    in 8L..14L -> AnalyticsGoalLockDurationDaysBucket.EIGHT_TO_FOURTEEN
    in 15L..30L -> AnalyticsGoalLockDurationDaysBucket.FIFTEEN_TO_THIRTY
    else -> AnalyticsGoalLockDurationDaysBucket.THIRTY_ONE_PLUS
}

package com.uiery.keep.domain.usageinsight

import com.uiery.keep.domain.firstpromise.UsageDataQuality
import com.uiery.keep.domain.firstpromise.UsagePatternType
import java.time.LocalDate

data class OnboardingUsageAggregate(
    val packageName: String,
    val totalForegroundMillis: Long,
    val lastUsedEpochMillis: Long,
    val localDate: LocalDate,
)

data class OnboardingUsageInterval(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
    val localDate: LocalDate,
)

data class EnabledRoutineCoverage(
    val packageNames: Set<String>,
    val startMinutes: Int,
    val endMinutes: Int,
    val repeatDays: Set<Int>,
)

internal data class UsageTimeBucketEvidence(
    val startMinutes: Int,
    val totalMillis: Long,
    val distinctDays: Int,
    val lastObservedEpochMillis: Long,
)

enum class InsufficientReason {
    QueryFailed,
    InsufficientUsageCoverage,
    NoCandidate,
}

sealed interface OnboardingUsageProfileResult {
    data class Ready(val profile: OnboardingUsageProfile) : OnboardingUsageProfileResult

    data class Insufficient(
        val usageCoverageDays: Int,
        val eventCoverageDays: Int,
        val reason: InsufficientReason,
    ) : OnboardingUsageProfileResult
}

data class OnboardingUsageProfile(
    val packageName: String,
    val appLabel: String,
    val averageDailyMinutes: Long,
    val suggestedStartMinutes: Int,
    val usageCoverageDays: Int,
    val eventCoverageDays: Int,
    val dataQuality: UsageDataQuality,
    val patternType: UsagePatternType,
)

package com.uiery.keep.domain.usageinsight

import java.time.Duration
import java.time.LocalDate
import kotlinx.datetime.LocalTime

/** 완결된 하루의 단일 앱 사용 집계. [nightUsage]는 00:00~06:00 구간과의 겹침. */
data class AppUsageDay(
    val date: LocalDate,
    val packageName: String,
    val totalUsage: Duration,
    val launchCount: Int,
    val nightUsage: Duration,
)

enum class UsageInsightType(val analyticsValue: String) {
    NightOwl("night_owl"),
    WeeklySurge("weekly_surge"),
}

sealed interface UsageInsight {
    val type: UsageInsightType
    val packageName: String

    data class NightOwl(
        override val packageName: String,
        val nightsCount: Int,
        val avgNightUsage: Duration,
    ) : UsageInsight {
        override val type: UsageInsightType get() = UsageInsightType.NightOwl
    }

    data class WeeklySurge(
        override val packageName: String,
        val thisWeekUsage: Duration,
        val lastWeekUsage: Duration,
    ) : UsageInsight {
        override val type: UsageInsightType get() = UsageInsightType.WeeklySurge
    }
}

data class UsageInsightRoutinePrefill(
    val packages: List<String>,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

fun UsageInsight.toRoutinePrefill(): UsageInsightRoutinePrefill = when (this) {
    is UsageInsight.NightOwl -> UsageInsightRoutinePrefill(
        packages = listOf(packageName),
        startTime = LocalTime(23, 30),
        endTime = LocalTime(7, 0),
    )
    is UsageInsight.WeeklySurge -> UsageInsightRoutinePrefill(
        packages = listOf(packageName),
        startTime = LocalTime(21, 0),
        endTime = LocalTime(23, 0),
    )
}

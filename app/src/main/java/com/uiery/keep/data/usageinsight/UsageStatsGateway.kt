package com.uiery.keep.data.usageinsight

import com.uiery.keep.domain.usageinsight.AppUsageDay
import java.time.LocalDate
import java.time.ZoneId

/** UsageStatsManager/AppOpsManager 프레임워크 접근을 감싼 게이트웨이. JVM 테스트에서는 fake로 대체한다. */
interface UsageStatsGateway {
    fun isPermissionGranted(): Boolean
    fun queryDailyUsage(from: LocalDate, toInclusive: LocalDate): List<AppUsageDay>
    fun queryOnboardingDailyAggregates(
        days: ClosedRange<LocalDate>,
        zoneId: ZoneId,
    ): List<AppUsageAggregateDay>
    fun appLabel(packageName: String): String?

    /** 인사이트 후보에서 제외할 패키지(예: 시스템 설정 앱). */
    fun insightExcludedPackages(): Set<String>
}

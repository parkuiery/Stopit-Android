package com.uiery.keep.data.usageinsight

import com.uiery.keep.domain.usageinsight.AppUsageDay
import java.time.LocalDate

/** UsageStatsManager/AppOpsManager 프레임워크 접근을 감싼 게이트웨이. JVM 테스트에서는 fake로 대체한다. */
interface UsageStatsGateway {
    fun isPermissionGranted(): Boolean
    fun queryDailyUsage(from: LocalDate, toInclusive: LocalDate): List<AppUsageDay>
    fun appLabel(packageName: String): String?
}

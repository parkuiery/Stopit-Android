package com.uiery.keep.feature.home

import com.uiery.keep.data.usageinsight.UsageInsightCardStateStore
import com.uiery.keep.data.usageinsight.UsageInsightRepository
import com.uiery.keep.data.usageinsight.UsageStatsGateway
import com.uiery.keep.database.dao.AppUsageDailyDao
import com.uiery.keep.database.entity.AppUsageDailyEntity
import com.uiery.keep.domain.usageinsight.AppUsageDay
import com.uiery.keep.feature.review.FakeDataStore
import java.time.Duration
import java.time.LocalDate

/** JVM 테스트용 UsageStatsManager 게이트웨이 fake. 권한/일별 사용량/앱 라벨을 주입한다. */
internal class FakeUsageStatsGateway(
    // 홈 복귀 재평가 테스트에서 권한 상태를 런타임에 뒤집을 수 있도록 var 로 노출한다.
    var permissionGranted: Boolean = true,
    private val dailyUsage: List<AppUsageDay> = emptyList(),
    private val labels: Map<String, String> = emptyMap(),
) : UsageStatsGateway {
    override fun isPermissionGranted(): Boolean = permissionGranted

    override fun queryDailyUsage(from: LocalDate, toInclusive: LocalDate): List<AppUsageDay> =
        dailyUsage.filter { !it.date.isBefore(from) && !it.date.isAfter(toInclusive) }

    override fun appLabel(packageName: String): String? = labels[packageName]

    override fun insightExcludedPackages(): Set<String> = emptySet()
}

/** date(ISO) + package 기준 인메모리 캐시 DAO. ISO 문자열은 사전식 비교로 날짜 순서를 만족한다. */
internal class InMemoryAppUsageDailyDao(
    seed: List<AppUsageDailyEntity> = emptyList(),
) : AppUsageDailyDao {
    private val store = linkedMapOf<Pair<String, String>, AppUsageDailyEntity>()

    init {
        seed.forEach { store[it.date to it.packageName] = it }
    }

    override suspend fun upsertAll(entries: List<AppUsageDailyEntity>) {
        entries.forEach { store[it.date to it.packageName] = it }
    }

    override suspend fun getSince(fromDate: String): List<AppUsageDailyEntity> =
        store.values.filter { it.date >= fromDate }.sortedBy { it.date }

    override suspend fun latestDate(): String? = store.values.maxOfOrNull { it.date }

    override suspend fun deleteBefore(beforeDate: String) {
        store.entries.removeAll { it.value.date < beforeDate }
    }
}

/** HomeViewModel 테스트가 기본으로 쓰는 UsageInsightRepository. 기본값은 Hidden(권한 O, 사용량 없음). */
internal fun homeUsageInsightRepository(
    dataStore: FakeDataStore,
    gateway: UsageStatsGateway = FakeUsageStatsGateway(),
): UsageInsightRepository = UsageInsightRepository(
    gateway = gateway,
    appUsageDailyDao = InMemoryAppUsageDailyDao(),
    cardStateStore = UsageInsightCardStateStore(dataStore),
)

/** 지정 패키지가 최근 1주 내 3일 이상 야간 사용 임계치를 넘기는 NightOwl 게이트웨이. */
internal fun nightOwlUsageStatsGateway(
    packageName: String = "com.instagram.android",
    appLabel: String = "Instagram",
    today: LocalDate = LocalDate.now(),
    permissionGranted: Boolean = true,
): FakeUsageStatsGateway {
    val nights = (1..3).map { offset ->
        AppUsageDay(
            date = today.minusDays(offset.toLong()),
            packageName = packageName,
            totalUsage = Duration.ofHours(1),
            launchCount = 5,
            nightUsage = Duration.ofMinutes(40),
        )
    }
    return FakeUsageStatsGateway(
        permissionGranted = permissionGranted,
        dailyUsage = nights,
        labels = mapOf(packageName to appLabel),
    )
}

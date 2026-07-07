package com.uiery.keep.data.usageinsight

import com.uiery.keep.database.dao.AppUsageDailyDao
import com.uiery.keep.database.entity.AppUsageDailyEntity
import com.uiery.keep.domain.usageinsight.AppUsageDay
import com.uiery.keep.domain.usageinsight.UsageInsight
import com.uiery.keep.domain.usageinsight.UsageInsightType
import com.uiery.keep.feature.review.FakeDataStore
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageInsightRepositoryTest {

    private val today: LocalDate = LocalDate.of(2026, 7, 3)

    private fun usageDay(
        daysAgo: Long,
        packageName: String = "com.test",
        totalMinutes: Long = 10,
        nightMinutes: Long = 0,
        launchCount: Int = 1,
    ) = AppUsageDay(
        date = today.minusDays(daysAgo),
        packageName = packageName,
        totalUsage = Duration.ofMinutes(totalMinutes),
        launchCount = launchCount,
        nightUsage = Duration.ofMinutes(nightMinutes),
    )

    private fun usageDayEntity(
        daysAgo: Long,
        packageName: String = "com.test",
        totalMinutes: Long = 10,
        nightMinutes: Long = 0,
        launchCount: Int = 1,
    ) = AppUsageDailyEntity(
        date = today.minusDays(daysAgo).toString(),
        packageName = packageName,
        totalUsageMillis = Duration.ofMinutes(totalMinutes).toMillis(),
        launchCount = launchCount,
        nightUsageMillis = Duration.ofMinutes(nightMinutes).toMillis(),
    )

    private fun repository(
        gateway: FakeUsageStatsGateway = FakeUsageStatsGateway(),
        dao: FakeAppUsageDailyDao = FakeAppUsageDailyDao(),
        store: UsageInsightCardStateStore = UsageInsightCardStateStore(FakeDataStore()),
    ) = UsageInsightRepository(gateway, dao, store)

    @Test
    fun `권한 없으면 PermissionNeeded, 수집 호출 없음`() = runBlocking {
        val gateway = FakeUsageStatsGateway(permissionGranted = false)
        val result = repository(gateway = gateway).currentInsightCard(today)
        assertEquals(UsageInsightCardResult.PermissionNeeded, result)
        assertTrue(gateway.queriedRanges.isEmpty())
    }

    @Test
    fun `권한 카드가 억제 중이면 권한 없어도 Hidden`() = runBlocking {
        val gateway = FakeUsageStatsGateway(permissionGranted = false)
        val store = UsageInsightCardStateStore(FakeDataStore())
        store.recordPermissionCardDismissed(dismissedOn = today.minusDays(1))
        val result = repository(gateway = gateway, store = store).currentInsightCard(today)
        assertEquals(UsageInsightCardResult.Hidden, result)
    }

    @Test
    fun `캐시가 비어 있으면 14일 전부터 어제까지 수집한다`() = runBlocking {
        val gateway = FakeUsageStatsGateway()
        repository(gateway = gateway).currentInsightCard(today)
        assertEquals(listOf(today.minusDays(14) to today.minusDays(1)), gateway.queriedRanges)
    }

    @Test
    fun `캐시 최신일이 어제면 재수집하지 않는다`() = runBlocking {
        val gateway = FakeUsageStatsGateway()
        val dao = FakeAppUsageDailyDao()
        dao.seed(usageDayEntity(daysAgo = 1))
        repository(gateway = gateway, dao = dao).currentInsightCard(today)
        assertTrue(gateway.queriedRanges.isEmpty())
    }

    @Test
    fun `캐시 최신일 다음날부터 어제까지만 증분 수집한다`() = runBlocking {
        val gateway = FakeUsageStatsGateway()
        val dao = FakeAppUsageDailyDao()
        dao.seed(usageDayEntity(daysAgo = 5))
        repository(gateway = gateway, dao = dao).currentInsightCard(today)
        assertEquals(listOf(today.minusDays(4) to today.minusDays(1)), gateway.queriedRanges)
    }

    @Test
    fun `90일 이전 데이터는 삭제된다`() = runBlocking {
        val dao = FakeAppUsageDailyDao()
        val old = usageDayEntity(daysAgo = 95)
        val recent = usageDayEntity(daysAgo = 1)
        dao.seed(old, recent)
        repository(dao = dao).currentInsightCard(today)
        assertFalse(dao.store.containsKey(old.date to old.packageName))
        assertTrue(dao.store.containsKey(recent.date to recent.packageName))
    }

    @Test
    fun `NightOwl 조건 충족 시 Ready에 앱 라벨이 담긴다`() = runBlocking {
        val dao = FakeAppUsageDailyDao()
        dao.seed(
            usageDayEntity(daysAgo = 1, packageName = "com.youtube", nightMinutes = 40),
            usageDayEntity(daysAgo = 2, packageName = "com.youtube", nightMinutes = 35),
            usageDayEntity(daysAgo = 4, packageName = "com.youtube", nightMinutes = 45),
        )
        val gateway = FakeUsageStatsGateway(labels = mapOf("com.youtube" to "YouTube"))
        val result = repository(gateway = gateway, dao = dao).currentInsightCard(today)
        val ready = result as UsageInsightCardResult.Ready
        assertTrue(ready.insight is UsageInsight.NightOwl)
        assertEquals("YouTube", ready.appLabel)
    }

    @Test
    fun `앱 라벨을 못 구하면 패키지명을 라벨로 사용한다`() = runBlocking {
        val dao = FakeAppUsageDailyDao()
        dao.seed(
            usageDayEntity(daysAgo = 1, packageName = "com.youtube", nightMinutes = 40),
            usageDayEntity(daysAgo = 2, packageName = "com.youtube", nightMinutes = 35),
            usageDayEntity(daysAgo = 4, packageName = "com.youtube", nightMinutes = 45),
        )
        val result = repository(dao = dao).currentInsightCard(today)
        val ready = result as UsageInsightCardResult.Ready
        assertEquals("com.youtube", ready.appLabel)
    }

    @Test
    fun `인사이트 없으면 Hidden`() = runBlocking {
        val dao = FakeAppUsageDailyDao()
        dao.seed(usageDayEntity(daysAgo = 1, packageName = "com.test", totalMinutes = 5))
        val result = repository(dao = dao).currentInsightCard(today)
        assertEquals(UsageInsightCardResult.Hidden, result)
    }

    @Test
    fun `수집된 데이터가 엔티티 왕복을 거쳐 인사이트 평가에 반영된다`() = runBlocking {
        val gateway = FakeUsageStatsGateway(
            daysToReturn = listOf(
                usageDay(daysAgo = 1, packageName = "com.youtube", totalMinutes = 90, nightMinutes = 40, launchCount = 3),
                usageDay(daysAgo = 2, packageName = "com.youtube", totalMinutes = 90, nightMinutes = 40, launchCount = 3),
                usageDay(daysAgo = 3, packageName = "com.youtube", totalMinutes = 90, nightMinutes = 40, launchCount = 3),
            ),
        )
        val dao = FakeAppUsageDailyDao()
        val result = repository(gateway = gateway, dao = dao).currentInsightCard(today)
        val nightOwl = (result as UsageInsightCardResult.Ready).insight as UsageInsight.NightOwl
        assertEquals("com.youtube", nightOwl.packageName)
        assertEquals(3, nightOwl.nightsCount)
        assertEquals(Duration.ofMinutes(40), nightOwl.avgNightUsage)
        assertEquals(
            AppUsageDailyEntity(
                date = today.minusDays(1).toString(),
                packageName = "com.youtube",
                totalUsageMillis = Duration.ofMinutes(90).toMillis(),
                launchCount = 3,
                nightUsageMillis = Duration.ofMinutes(40).toMillis(),
            ),
            dao.store[today.minusDays(1).toString() to "com.youtube"],
        )
        assertEquals(3, dao.store.size)
    }

    @Test
    fun `캐시 최신일이 14일 이전이면 수집 시작일을 14일 전으로 클램프한다`() = runBlocking {
        val gateway = FakeUsageStatsGateway()
        val dao = FakeAppUsageDailyDao()
        dao.seed(usageDayEntity(daysAgo = 20))
        repository(gateway = gateway, dao = dao).currentInsightCard(today)
        assertEquals(listOf(today.minusDays(14) to today.minusDays(1)), gateway.queriedRanges)
    }

    @Test
    fun `dismiss한 타입은 다음 평가에서 제외된다`() = runBlocking {
        val dao = FakeAppUsageDailyDao()
        dao.seed(
            usageDayEntity(daysAgo = 1, packageName = "com.youtube", nightMinutes = 40),
            usageDayEntity(daysAgo = 2, packageName = "com.youtube", nightMinutes = 35),
            usageDayEntity(daysAgo = 4, packageName = "com.youtube", nightMinutes = 45),
        )
        val store = UsageInsightCardStateStore(FakeDataStore())
        store.recordDismissed(UsageInsightType.NightOwl, dismissedOn = today)
        val result = repository(dao = dao, store = store).currentInsightCard(today)
        assertEquals(UsageInsightCardResult.Hidden, result)
    }

    @Test
    fun `제외 패키지의 캐시 데이터는 인사이트 평가에서 무시된다`() = runBlocking {
        val dao = FakeAppUsageDailyDao()
        dao.seed(
            usageDayEntity(daysAgo = 1, packageName = "com.android.settings", nightMinutes = 40),
            usageDayEntity(daysAgo = 2, packageName = "com.android.settings", nightMinutes = 35),
            usageDayEntity(daysAgo = 4, packageName = "com.android.settings", nightMinutes = 45),
        )
        val gateway = FakeUsageStatsGateway(excludedPackages = setOf("com.android.settings"))
        val result = repository(gateway = gateway, dao = dao).currentInsightCard(today)
        assertEquals(UsageInsightCardResult.Hidden, result)
    }
}

private class FakeUsageStatsGateway(
    var permissionGranted: Boolean = true,
    var daysToReturn: List<AppUsageDay> = emptyList(),
    var labels: Map<String, String> = emptyMap(),
    var excludedPackages: Set<String> = emptySet(),
) : UsageStatsGateway {
    val queriedRanges = mutableListOf<Pair<LocalDate, LocalDate>>()

    override fun isPermissionGranted(): Boolean = permissionGranted

    override fun queryDailyUsage(from: LocalDate, toInclusive: LocalDate): List<AppUsageDay> {
        queriedRanges += from to toInclusive
        return daysToReturn.filter { !it.date.isBefore(from) && !it.date.isAfter(toInclusive) }
    }

    override fun appLabel(packageName: String): String? = labels[packageName]

    override fun insightExcludedPackages(): Set<String> = excludedPackages
}

private class FakeAppUsageDailyDao : AppUsageDailyDao {
    val store = mutableMapOf<Pair<String, String>, AppUsageDailyEntity>()

    fun seed(vararg entries: AppUsageDailyEntity) {
        entries.forEach { store[it.date to it.packageName] = it }
    }

    override suspend fun upsertAll(entries: List<AppUsageDailyEntity>) {
        entries.forEach { store[it.date to it.packageName] = it }
    }

    override suspend fun getSince(fromDate: String): List<AppUsageDailyEntity> =
        store.values.filter { it.date >= fromDate }.sortedBy { it.date }

    override suspend fun latestDate(): String? = store.keys.maxOfOrNull { it.first }

    override suspend fun deleteBefore(beforeDate: String) {
        store.keys.filter { it.first < beforeDate }.forEach { store.remove(it) }
    }
}

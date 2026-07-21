package com.uiery.keep.data.usageinsight

import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.domain.firstpromise.UsageDataQuality
import com.uiery.keep.domain.usageinsight.InsufficientReason
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfileResult
import com.uiery.keep.model.RoutineModel
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OnboardingUsageProfileRepositoryTest {
    private val today = LocalDate.of(2026, 7, 15)
    private val zoneId = ZoneId.of("Asia/Seoul")
    private val expectedDays = today.minusDays(7)..today.minusDays(1)

    @Test
    fun `repository requests prior seven local dates and keeps usage and event coverage independent`() = runBlocking {
        val gateway = FakeOnboardingGateway(
            aggregates = aggregates("com.video", 3, 30),
            intervals = listOf(interval("com.video", today.minusDays(1), 20, 0, 20, 30)),
            labels = mapOf("com.video" to "Video"),
        )

        val profile = repository(gateway).profile(today, zoneId, 21 * 60).ready()

        assertEquals(listOf(expectedDays to zoneId), gateway.aggregateCalls)
        assertEquals(listOf(expectedDays to zoneId), gateway.intervalCalls)
        assertEquals(3, profile.usageCoverageDays)
        assertEquals(1, profile.eventCoverageDays)
        assertEquals(30, profile.averageDailyMinutes)
        assertEquals(UsageDataQuality.UsageOnly, profile.dataQuality)
    }

    @Test
    fun `disabled and partial routines do not become a package exclusion set`() = runBlocking {
        val gateway = FakeOnboardingGateway(
            aggregates = aggregates("com.video", 3, 60),
            labels = mapOf("com.video" to "Video"),
        )
        val routines = listOf(
            routine(enabled = false, startMinutes = 21 * 60, endMinutes = 22 * 60),
            routine(enabled = true, startMinutes = 21 * 60 + 15, endMinutes = 22 * 60),
            routine(enabled = true, packageName = "com.other", startMinutes = 21 * 60, endMinutes = 22 * 60),
        )

        val result = repository(gateway, routines).profile(today, zoneId, 21 * 60)

        assertTrue(result is OnboardingUsageProfileResult.Ready)
    }

    @Test
    fun `enabled routine with full package time and day coverage excludes candidate`() = runBlocking {
        val gateway = FakeOnboardingGateway(
            aggregates = aggregates("com.video", 3, 60),
            labels = mapOf("com.video" to "Video"),
        )
        val fullCoverage = routine(
            enabled = true,
            startMinutes = 20 * 60,
            endMinutes = 22 * 60,
            repeatDays = "1111111",
        )

        val result = repository(gateway, listOf(fullCoverage)).profile(today, zoneId, 21 * 60)

        assertEquals(InsufficientReason.NoCandidate, result.insufficient().reason)
    }

    @Test
    fun `null and blank labels skip ranked candidates without package fallback`() = runBlocking {
        val gateway = FakeOnboardingGateway(
            aggregates = aggregates("com.first", 3, 90) +
                aggregates("com.second", 3, 60) +
                aggregates("com.third", 3, 30),
            labels = mapOf("com.second" to "   ", "com.third" to "Third"),
        )

        val profile = repository(gateway).profile(today, zoneId, 21 * 60).ready()

        assertEquals("com.third", profile.packageName)
        assertEquals("Third", profile.appLabel)

        val noLabels = FakeOnboardingGateway(
            aggregates = aggregates("com.first", 3, 90),
            labels = emptyMap(),
        )
        val insufficient = repository(noLabels).profile(today, zoneId, 21 * 60).insufficient()
        assertEquals(InsufficientReason.NoCandidate, insufficient.reason)
    }

    @Test
    fun `gateway and routine exceptions map to typed query failure`() = runBlocking {
        val gatewayFailure = FakeOnboardingGateway(queryFailure = IllegalStateException("framework"))
        val first = repository(gatewayFailure).profile(today, zoneId, 21 * 60).insufficient()
        assertEquals(InsufficientReason.QueryFailed, first.reason)
        assertEquals(0, first.usageCoverageDays)
        assertEquals(0, first.eventCoverageDays)

        val routineFailure = object : FakeRoutineRepository() {
            override suspend fun fetchAllOnce(): List<RoutineModel> = error("database")
        }
        val second = OnboardingUsageProfileRepository(
            gateway = FakeOnboardingGateway(),
            routineRepository = routineFailure,
        ).profile(today, zoneId, 21 * 60).insufficient()
        assertEquals(InsufficientReason.QueryFailed, second.reason)
    }

    @Test
    fun `cooperative cancellation and coroutine timeout escape instead of becoming query failure`() = runBlocking {
        val cancellation = CancellationException("cancel profile")
        val cancellingGateway = FakeOnboardingGateway(queryFailure = cancellation)

        try {
            repository(cancellingGateway).profile(today, zoneId, 21 * 60)
            fail("CancellationException must escape")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }

        val suspendingRoutines = object : FakeRoutineRepository() {
            override suspend fun fetchAllOnce(): List<RoutineModel> {
                delay(Long.MAX_VALUE)
                return emptyList()
            }
        }
        try {
            withTimeout(10) {
                OnboardingUsageProfileRepository(
                    gateway = FakeOnboardingGateway(),
                    routineRepository = suspendingRoutines,
                ).profile(today, zoneId, 21 * 60)
            }
            fail("TimeoutCancellationException must escape")
        } catch (_: TimeoutCancellationException) {
            // Expected: structured coroutine cancellation is never converted into product fallback.
        }
    }

    @Test
    fun `fatal errors escape while ordinary exceptions remain typed query failures`() = runBlocking {
        val ordinary = repository(
            FakeOnboardingGateway(queryFailure = IllegalStateException("framework")),
        ).profile(today, zoneId, 21 * 60).insufficient()
        assertEquals(InsufficientReason.QueryFailed, ordinary.reason)

        val fatal = AssertionError("fatal invariant")
        try {
            repository(FakeOnboardingGateway(queryFailure = fatal)).profile(today, zoneId, 21 * 60)
            fail("Error must escape")
        } catch (actual: AssertionError) {
            assertSame(fatal, actual)
        }
    }

    @Test
    fun `repository passes midnight enabled routine coverage with parsed weekdays`() = runBlocking {
        val gateway = FakeOnboardingGateway(
            aggregates = aggregates("com.video", 3, 60),
            intervals = (1L..3L).map { daysAgo ->
                interval("com.video", today.minusDays(daysAgo), 23, 30, 0, 0)
            },
            labels = mapOf("com.video" to "Video"),
        )
        val midnightRoutine = routine(
            enabled = true,
            startMinutes = 23 * 60,
            endMinutes = 30,
            repeatDays = "1111111",
        )

        val result = repository(gateway, listOf(midnightRoutine)).profile(today, zoneId, 21 * 60)

        assertEquals(InsufficientReason.NoCandidate, result.insufficient().reason)
    }

    private fun repository(
        gateway: UsageStatsGateway,
        routines: List<RoutineModel> = emptyList(),
    ) = OnboardingUsageProfileRepository(gateway, FakeRoutineRepository(routines))

    private fun aggregates(packageName: String, days: Int, dailyMinutes: Long) =
        (1..days).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            AppUsageAggregateDay(
                packageName = packageName,
                totalForegroundMillis = Duration.ofMinutes(dailyMinutes).toMillis(),
                lastUsedEpochMillis = date.atTime(20, 0).atZone(zoneId).toInstant().toEpochMilli(),
                localDate = date,
            )
        }

    private fun interval(
        packageName: String,
        date: LocalDate,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
    ): AppUsageInterval {
        val start = date.atTime(startHour, startMinute).atZone(zoneId).toInstant().toEpochMilli()
        val endDate = if (endHour * 60 + endMinute <= startHour * 60 + startMinute) date.plusDays(1) else date
        return AppUsageInterval(
            packageName = packageName,
            startMillis = start,
            endMillis = endDate.atTime(endHour, endMinute).atZone(zoneId).toInstant().toEpochMilli(),
            localDate = date,
            countsAsLaunch = true,
        )
    }

    private fun routine(
        enabled: Boolean,
        packageName: String = "com.video",
        startMinutes: Int,
        endMinutes: Int,
        repeatDays: String = "1111111",
    ) = RoutineModel(
        id = 1,
        name = "Routine",
        startTime = LocalTime(startMinutes / 60, startMinutes % 60),
        endTime = LocalTime(endMinutes / 60, endMinutes % 60),
        repeatDays = repeatDays,
        lockApplications = listOf(packageName),
        isEnabled = enabled,
    )

    private fun OnboardingUsageProfileResult.ready() = (this as OnboardingUsageProfileResult.Ready).profile
    private fun OnboardingUsageProfileResult.insufficient() = this as OnboardingUsageProfileResult.Insufficient
}

private class FakeOnboardingGateway(
    private val aggregates: List<AppUsageAggregateDay> = emptyList(),
    private val intervals: List<AppUsageInterval> = emptyList(),
    private val labels: Map<String, String> = emptyMap(),
    private val queryFailure: Throwable? = null,
) : UsageStatsGateway {
    val aggregateCalls = mutableListOf<Pair<ClosedRange<LocalDate>, ZoneId>>()
    val intervalCalls = mutableListOf<Pair<ClosedRange<LocalDate>, ZoneId>>()

    override fun isPermissionGranted() = true
    override fun queryDailyUsage(from: LocalDate, toInclusive: LocalDate) = emptyList<com.uiery.keep.domain.usageinsight.AppUsageDay>()
    override fun queryOnboardingDailyAggregates(days: ClosedRange<LocalDate>, zoneId: ZoneId): List<AppUsageAggregateDay> {
        aggregateCalls += days to zoneId
        queryFailure?.let { throw it }
        return aggregates
    }
    override fun queryOnboardingUsageIntervals(days: ClosedRange<LocalDate>, zoneId: ZoneId): List<AppUsageInterval> {
        intervalCalls += days to zoneId
        queryFailure?.let { throw it }
        return intervals
    }
    override fun appLabel(packageName: String): String? = labels[packageName]
    override fun insightExcludedPackages(): Set<String> = emptySet()
}

private open class FakeRoutineRepository(
    private val routines: List<RoutineModel> = emptyList(),
) : RoutineRepository {
    override fun fetchAll(): Flow<List<RoutineModel>> = flowOf(routines)
    override suspend fun fetchAllOnce(): List<RoutineModel> = routines
}

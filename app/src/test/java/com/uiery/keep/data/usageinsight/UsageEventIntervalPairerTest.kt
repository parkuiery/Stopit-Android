package com.uiery.keep.data.usageinsight

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageEventIntervalPairerTest {

    private val zoneId = ZoneId.of("Asia/Seoul")
    private val day = LocalDate.of(2026, 7, 14)

    @Test
    fun `normal pair keeps earliest unmatched resume and counts one launch`() {
        val result = pair(
            events = listOf(
                event("com.video", LocalTime.of(10, 0), ForegroundEventType.Resumed),
                event("com.video", LocalTime.of(10, 5), ForegroundEventType.Resumed),
                event("com.video", LocalTime.of(10, 30), ForegroundEventType.Paused),
            ),
        )

        assertEquals(
            listOf(
                AppUsageInterval(
                    packageName = "com.video",
                    startMillis = epoch(LocalTime.of(10, 0)),
                    endMillis = epoch(LocalTime.of(10, 30)),
                    localDate = day,
                    countsAsLaunch = true,
                ),
            ),
            result,
        )
    }

    @Test
    fun `first orphan close carries session from day start and later orphan is ignored`() {
        val result = pair(
            events = listOf(
                event("com.video", LocalTime.of(0, 20), ForegroundEventType.Paused),
                event("com.video", LocalTime.of(0, 30), ForegroundEventType.Stopped),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(dayStart(), result.single().startMillis)
        assertEquals(epoch(LocalTime.of(0, 20)), result.single().endMillis)
        assertFalse(result.single().countsAsLaunch)
    }

    @Test
    fun `unclosed in-day resume closes at day end`() {
        val interval = pair(
            events = listOf(
                event("com.video", LocalTime.of(22, 45), ForegroundEventType.Resumed),
            ),
        ).single()

        assertEquals(epoch(LocalTime.of(22, 45)), interval.startMillis)
        assertEquals(dayEnd(), interval.endMillis)
        assertTrue(interval.countsAsLaunch)
    }

    @Test
    fun `intervals clamp to request bounds and zero or negative spans disappear`() {
        val result = pair(
            events = listOf(
                event("com.video", LocalTime.of(9, 30), ForegroundEventType.Resumed),
                event("com.video", LocalTime.of(10, 15), ForegroundEventType.Paused),
                event("com.zero", LocalTime.of(11, 0), ForegroundEventType.Resumed),
                event("com.zero", LocalTime.of(11, 0), ForegroundEventType.Stopped),
                event("com.negative", LocalTime.of(10, 30), ForegroundEventType.Resumed),
                event("com.negative", LocalTime.of(10, 20), ForegroundEventType.Stopped),
            ),
            requestStartMillis = epoch(LocalTime.of(10, 0)),
            requestEndExclusiveMillis = epoch(LocalTime.of(11, 0)),
        )

        assertEquals(1, result.size)
        assertEquals("com.video", result.single().packageName)
        assertEquals(epoch(LocalTime.of(10, 0)), result.single().startMillis)
        assertEquals(epoch(LocalTime.of(10, 15)), result.single().endMillis)
    }

    @Test
    fun `cross-midnight session is split and carried half is not a launch`() {
        val nextDay = day.plusDays(1)
        val beforeMidnight = pair(
            events = listOf(
                event("com.video", LocalTime.of(23, 50), ForegroundEventType.Resumed),
            ),
        ).single()
        val afterMidnight = pair(
            date = nextDay,
            events = listOf(
                ForegroundEventSample(
                    packageName = "com.video",
                    timestampMillis = nextDay.atTime(0, 10).atZone(zoneId).toInstant().toEpochMilli(),
                    type = ForegroundEventType.Paused,
                ),
            ),
        ).single()

        assertEquals(dayEnd(), beforeMidnight.endMillis)
        assertTrue(beforeMidnight.countsAsLaunch)
        assertEquals(dayEnd(), afterMidnight.startMillis)
        assertFalse(afterMidnight.countsAsLaunch)
    }

    @Test
    fun `DST calendar boundary uses local next midnight rather than fixed 24 hours`() {
        val newYork = ZoneId.of("America/New_York")
        val springForwardDay = LocalDate.of(2026, 3, 8)
        val start = springForwardDay.atStartOfDay(newYork).toInstant().toEpochMilli()
        val interval = pair(
            date = springForwardDay,
            zone = newYork,
            events = listOf(
                ForegroundEventSample("com.video", start, ForegroundEventType.Resumed),
            ),
        ).single()

        assertEquals(Duration.ofHours(23).toMillis(), interval.endMillis - interval.startMillis)
        assertEquals(springForwardDay, interval.localDate)
    }

    @Test
    fun `own settings excluded and non-launchable packages are filtered`() {
        val pairer = UsageEventIntervalPairer(
            ownPackageName = "com.keep",
            excludedPackages = setOf("com.blocked", "com.android.settings"),
            isLaunchable = { it != "com.no.launcher" },
        )
        val packages = listOf(
            "com.keep",
            "com.android.settings",
            "com.blocked",
            "com.no.launcher",
            "com.allowed",
        )
        val result = pairer.pair(
            localDate = day,
            zoneId = zoneId,
            requestStartMillis = dayStart(),
            requestEndExclusiveMillis = dayEnd(),
            events = packages.flatMapIndexed { index, packageName ->
                val start = epoch(LocalTime.of(8 + index, 0))
                listOf(
                    ForegroundEventSample(packageName, start, ForegroundEventType.Resumed),
                    ForegroundEventSample(packageName, start + Duration.ofMinutes(5).toMillis(), ForegroundEventType.Paused),
                )
            },
        )

        assertEquals(listOf("com.allowed"), result.map { it.packageName })
    }

    @Test
    fun `daily summary preserves total launch count and night overlap from intervals`() {
        val intervals = listOf(
            interval("com.video", LocalTime.of(0, 0), LocalTime.of(1, 0), countsAsLaunch = false),
            interval("com.video", LocalTime.of(5, 30), LocalTime.of(6, 30), countsAsLaunch = true),
            interval("com.video", LocalTime.of(20, 0), LocalTime.of(20, 30), countsAsLaunch = true),
        )

        val summary = aggregateAppUsageIntervals(day, zoneId, intervals).single()

        assertEquals(Duration.ofMinutes(150), summary.totalUsage)
        assertEquals(2, summary.launchCount)
        assertEquals(Duration.ofMinutes(90), summary.nightUsage)
    }

    @Test
    fun `Home launch count keeps accepted resume whose paired interval is zero duration`() {
        val pairer = UsageEventIntervalPairer(
            ownPackageName = "com.keep",
            excludedPackages = emptySet(),
            isLaunchable = { true },
        )
        val reconstruction = pairer.reconstruct(
            localDate = day,
            zoneId = zoneId,
            requestStartMillis = dayStart(),
            requestEndExclusiveMillis = dayEnd(),
            events = listOf(
                event("com.video", LocalTime.of(10, 0), ForegroundEventType.Resumed),
                event("com.video", LocalTime.of(10, 10), ForegroundEventType.Paused),
                event("com.video", LocalTime.of(11, 0), ForegroundEventType.Resumed),
                event("com.video", LocalTime.of(11, 0), ForegroundEventType.Paused),
            ),
        )

        val summary = aggregateAppUsageIntervals(
            localDate = day,
            zoneId = zoneId,
            intervals = reconstruction.intervals,
            acceptedInDayLaunchCounts = reconstruction.acceptedInDayLaunchCounts,
        ).single()

        assertEquals(1, reconstruction.intervals.size)
        assertTrue(reconstruction.intervals.single().countsAsLaunch)
        assertEquals(Duration.ofMinutes(10), summary.totalUsage)
        assertEquals(2, summary.launchCount)
    }

    private fun pair(
        date: LocalDate = day,
        zone: ZoneId = zoneId,
        events: List<ForegroundEventSample>,
        requestStartMillis: Long = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        requestEndExclusiveMillis: Long = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    ): List<AppUsageInterval> = UsageEventIntervalPairer(
        ownPackageName = "com.keep",
        excludedPackages = setOf("com.android.settings"),
        isLaunchable = { true },
    ).pair(
        localDate = date,
        zoneId = zone,
        requestStartMillis = requestStartMillis,
        requestEndExclusiveMillis = requestEndExclusiveMillis,
        events = events,
    )

    private fun event(
        packageName: String,
        time: LocalTime,
        type: ForegroundEventType,
    ) = ForegroundEventSample(packageName, epoch(time), type)

    private fun interval(
        packageName: String,
        start: LocalTime,
        end: LocalTime,
        countsAsLaunch: Boolean,
    ) = AppUsageInterval(
        packageName = packageName,
        startMillis = epoch(start),
        endMillis = epoch(end),
        localDate = day,
        countsAsLaunch = countsAsLaunch,
    )

    private fun epoch(time: LocalTime): Long = day.atTime(time).atZone(zoneId).toInstant().toEpochMilli()

    private fun dayStart(): Long = day.atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun dayEnd(): Long = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
}

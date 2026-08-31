package com.uiery.keep.domain.pomodoro

import com.uiery.keep.analytics.AnalyticsPomodoroEndReason
import com.uiery.keep.analytics.AnalyticsPomodoroEntrySurface
import com.uiery.keep.data.lock.TimedLockStartOrigin
import com.uiery.keep.data.lock.TimedLockStartResult
import com.uiery.keep.data.lock.TimedLockStarter
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.database.repository.LockHistorySessionWriter
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PomodoroSessionStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.service.LockHistoryRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * `docs/POMODORO_FOCUS_MVP.md` 의 "Analytics 계약" 과 "잠금 계층 합류" 를 고정한다.
 *
 * 이 컨트롤러는 잠금을 스스로 만들지 않는다. 집중 세션은 잠금 관점에서 연속된 타이머 잠금 하나이고
 * 그 잠금은 [TimedLockStarter] 가 만든다. 여기 테스트가 지키는 것은 **그 합류가 실제로 일어나는지**다.
 */
class PomodoroSessionControllerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val start: Instant = Instant.parse("2026-08-27T00:00:00Z")
    private val blockedApps = setOf("com.video.app", "com.social.app")

    private fun at(minutes: Long): Instant = start.plus(Duration.ofMinutes(minutes))

    private class Harness(
        val store: PomodoroSessionStore,
        val analytics: RecordingPomodoroAnalytics,
        val lock: RecordingTimedLockStarter,
        val history: RecordingLockHistoryDao,
        val blockingStateStore: BlockingStateStore,
        val controller: PomodoroSessionController,
    )

    private fun harness(
        lockResult: TimedLockStartResult = TimedLockStartResult.Started(
            encodedDeadline = "2026-08-27T02:10:00Z",
            firstLockConfigured = false,
        ),
    ): Harness {
        val dataStore = FakeDataStore()
        val store = PomodoroSessionStore(dataStore)
        val blockingStateStore = BlockingStateStore(dataStore)
        val analytics = RecordingPomodoroAnalytics()
        val lock = RecordingTimedLockStarter(lockResult)
        val history = RecordingLockHistoryDao()
        val controller = PomodoroSessionController(
            store = store,
            analytics = analytics,
            timedLockStarter = lock,
            blockingStateStore = blockingStateStore,
            lockHistoryRecorder = LockHistoryRecorder(dataStore, LockHistorySessionWriter(history)),
        )
        return Harness(store, analytics, lock, history, blockingStateStore, controller)
    }

    private suspend fun Harness.startSession(
        cycle: PomodoroCycle = PomodoroCycle.Focus25,
        now: Instant = start,
        surface: String = AnalyticsPomodoroEntrySurface.HOME,
    ) = controller.start(
        cycle = cycle,
        entrySurface = surface,
        selectedPackages = blockedApps,
        hasWebTargets = false,
        now = now,
    )

    /**
     * 세션이 예약하는 잠금은 집중 하나가 아니라 사이클 전체다. `Focus25` 는 130분이다.
     * 여기가 틀리면 사용자가 건 시간과 실제 잠금 시간이 어긋난다.
     */
    @Test
    fun startReservesOneTimedLockForTheWholeCycle() = runBlocking {
        val h = harness()

        h.startSession()

        assertEquals(1, h.lock.started.size)
        val request = h.lock.started.single()
        assertEquals(TimedLockStartOrigin.Pomodoro, request.origin)
        assertEquals(130L, request.durationMinutes)
        assertEquals(blockedApps, request.packages)
        // commit 이 돌아야 lock_session_start 와 첫 잠금 전달이 나간다.
        assertEquals(1, h.lock.committed.size)
    }

    @Test
    fun startPersistsTheSessionAndReportsItsOwnPayload() = runBlocking {
        val h = harness()

        val result = h.startSession()

        assertTrue(result is PomodoroStartResult.Started)
        assertEquals(PomodoroPresetKind.Focus25, h.store.readStoredSession()?.cycle?.kind)
        assertEquals(listOf("25_5|home|2_3|25_34|4"), h.analytics.started)
    }

    /**
     * 이미 다른 타이머 잠금이 돌고 있으면 세션도 시작하지 않는다. 화면만 집중 중이라고 말하고
     * 실제 잠금은 다른 것이 쥐고 있는 상태를 만들지 않는다.
     */
    @Test
    fun startIsRefusedWhenTheLockCannotBeTaken() = runBlocking {
        val h = harness(lockResult = TimedLockStartResult.AlreadyActive)

        val result = h.startSession()

        assertTrue(result is PomodoroStartResult.LockUnavailable)
        assertNull(h.store.readStoredSession())
        assertTrue(h.analytics.started.isEmpty())
        assertTrue(h.lock.committed.isEmpty())
    }

    @Test
    fun startIsRefusedWhenThereIsNothingToBlock() = runBlocking {
        val h = harness(lockResult = TimedLockStartResult.EmptyApps)

        assertTrue(h.startSession() is PomodoroStartResult.LockUnavailable)
        assertNull(h.store.readStoredSession())
    }

    /**
     * 세션이 잠금 기록에 남아야 성과 리포트·주간 요약·완주율이 집중 세션을 본다.
     * 기록되는 구간은 예약 길이가 아니라 실제로 잠겨 있던 시간이다.
     */
    @Test
    fun endingByUserRecordsTheActualLockedSpanInHistory() = runBlocking {
        val h = harness()
        h.startSession()

        h.controller.endByUser(now = at(70), zone = zone)

        val recorded = h.history.inserted.single()
        assertEquals(start.toEpochMilli(), recorded.startTimestamp)
        assertEquals(at(70).toEpochMilli(), recorded.endTimestamp)
        assertEquals(Duration.ofMinutes(70).toMillis(), recorded.durationMillis)
        assertEquals(false, recorded.isRoutine)
    }

    /**
     * 세션을 끊으면 잠금도 함께 끝나야 한다. `LOCK_TIME` 이 남으면 세션은 끝났는데 타이머 잠금만
     * 예약된 시각까지 계속 도는, 사용자가 어디서도 끌 수 없는 상태가 된다.
     */
    @Test
    fun endingByUserReleasesTheTimedLock() = runBlocking {
        val h = harness()
        h.startSession()
        h.blockingStateStore.saveLockTime("2026-08-27T02:10:00Z")

        h.controller.endByUser(now = at(70), zone = zone)

        assertNull(h.blockingStateStore.readLockTime())
    }

    @Test
    fun aSessionThatRunsToTheEndAlsoReleasesTheLockAndRecordsHistory() = runBlocking {
        val h = harness()
        h.startSession()
        h.blockingStateStore.saveLockTime("2026-08-27T02:10:00Z")

        h.controller.sync(now = at(130), zone = zone)

        assertNull(h.blockingStateStore.readLockTime())
        assertEquals(1, h.history.inserted.size)
        assertEquals(
            listOf("25_5|${AnalyticsPomodoroEndReason.ALL_CYCLES_COMPLETED}|4_6|120_plus"),
            h.analytics.ended,
        )
    }

    @Test
    fun syncReportsEachFocusThatFinishedAndTheBreakTheUserLandedIn() = runBlocking {
        val h = harness()
        h.startSession()

        h.controller.sync(now = at(27), zone = zone)

        assertEquals(listOf("25_5|1"), h.analytics.focusCompleted)
        assertEquals(listOf("25_5|short"), h.analytics.breakStarted)
    }

    /**
     * 앱이 죽어 있는 동안 여러 구간이 지났다면 완료된 집중은 모두 세되, 휴식은 지금 들어선 하나만
     * 기록한다. 자는 동안 흘러간 휴식은 사용자가 시작한 적이 없다.
     */
    @Test
    fun syncAfterAProcessDeathCountsEveryFocusButOnlyTheCurrentBreak() = runBlocking {
        val h = harness()
        h.startSession()

        h.controller.sync(now = at(58), zone = zone)

        assertEquals(listOf("25_5|1", "25_5|2_3"), h.analytics.focusCompleted)
        assertEquals(listOf("25_5|short"), h.analytics.breakStarted)
    }

    @Test
    fun syncAccumulatesTodayFocusCountAcrossSeparateSessions() = runBlocking {
        val h = harness()

        h.startSession()
        h.controller.sync(now = at(58), zone = zone)
        assertEquals(2, h.controller.todayFocusCount(now = at(58), zone = zone))

        h.controller.clearFinishedSession()
        h.startSession(now = at(200))
        h.controller.sync(now = at(226), zone = zone)

        assertEquals(3, h.controller.todayFocusCount(now = at(226), zone = zone))
    }

    @Test
    fun aSessionFoundAlreadyOverIsReportedAsRecoveredNotAsCompleted() = runBlocking {
        val h = harness()
        h.startSession()

        // 한참 뒤에 앱을 열어 발견했다. 사용자는 완료를 보지 못했다.
        h.controller.sync(now = at(400), zone = zone)

        assertEquals(
            listOf("25_5|${AnalyticsPomodoroEndReason.EXPIRED_RECOVERY}|4_6|120_plus"),
            h.analytics.ended,
        )
    }

    @Test
    fun endByUserReportsWhatWasCompletedAndStopsTheSession() = runBlocking {
        val h = harness()
        h.startSession()

        val ended = h.controller.endByUser(now = at(70), zone = zone)

        assertEquals(PomodoroSessionStatus.EndedEarly, ended?.status)
        assertEquals(2, ended?.completedFocusCount)
        assertEquals(
            listOf("25_5|${AnalyticsPomodoroEndReason.USER_ENDED}|2_3|60_119"),
            h.analytics.ended,
        )
    }

    @Test
    fun endingATwiceEndedSessionDoesNotReportOrRecordTwice() = runBlocking {
        val h = harness()
        h.startSession()

        h.controller.endByUser(now = at(70), zone = zone)
        h.controller.endByUser(now = at(80), zone = zone)

        assertEquals(1, h.analytics.ended.size)
        assertEquals(1, h.history.inserted.size)
    }

    @Test
    fun clearFinishedSessionRemovesOnlyFinishedSessions() = runBlocking {
        val h = harness()
        h.startSession()

        h.controller.clearFinishedSession()
        assertEquals(PomodoroSessionStatus.Active, h.store.readStoredSession()?.status)

        h.controller.endByUser(now = at(10), zone = zone)
        h.controller.clearFinishedSession()
        assertNull(h.store.readStoredSession())
    }

    @Test
    fun syncWithoutAStoredSessionDoesNothing() = runBlocking {
        val h = harness()

        assertNull(h.controller.sync(now = start, zone = zone))
        assertTrue(h.analytics.ended.isEmpty())
        assertTrue(h.history.inserted.isEmpty())
    }

    /**
     * 커스텀은 `preset` 이 `custom` 이라 길이를 알 수 없다. `focus_minutes_bucket` 이 유일한
     * 길이 신호이고, 예약되는 잠금도 그 길이를 따라야 한다.
     */
    @Test
    fun aCustomCycleReservesItsOwnTotalAndReportsItsLengthBucket() = runBlocking {
        val h = harness()
        val cycle = PomodoroCycle.custom(
            focusMinutes = 40,
            shortBreakMinutes = 7,
            longBreakMinutes = 25,
        )!!

        h.startSession(cycle = cycle, surface = AnalyticsPomodoroEntrySurface.MENU)

        // 4×40 + 3×7 + 25 = 206분
        assertEquals(206L, h.lock.started.single().durationMinutes)
        assertEquals(listOf("custom|menu|2_3|35_49|4"), h.analytics.started)
        assertNotNull(h.store.readStoredSession())
    }

    @Test
    fun theStoredSelectedPackagesAreWhatEndsUpInHistory() = runBlocking {
        val h = harness()
        h.startSession()
        // 잠금 세션이 저장소에 남긴 선택 앱이 기록의 대상이 된다.
        h.blockingStateStore.saveSelectedAppPackages(blockedApps)

        h.controller.endByUser(now = at(30), zone = zone)

        assertEquals(blockedApps, h.history.inserted.single().lockedApps.toSet())
    }
}

private data class TimedLockStartRequest(
    val packages: Set<String>,
    val durationMinutes: Long,
    val origin: TimedLockStartOrigin,
)

private class RecordingTimedLockStarter(
    private val result: TimedLockStartResult,
) : TimedLockStarter {
    val started = mutableListOf<TimedLockStartRequest>()
    val committed = mutableListOf<TimedLockStartResult.Started>()

    override suspend fun start(
        packages: Set<String>,
        durationMinutes: Long,
        origin: TimedLockStartOrigin,
        targetDeadline: Instant?,
        hasWebTargets: Boolean,
    ): TimedLockStartResult {
        started += TimedLockStartRequest(packages, durationMinutes, origin)
        return result
    }

    override suspend fun commit(started: TimedLockStartResult.Started) {
        committed += started
    }

    override suspend fun rollback(started: TimedLockStartResult.Started): Boolean = true
}

/** 공유 fake 는 `insert` 가 no-op 이라 기록 여부를 볼 수 없다. 여기서는 실제로 받아 적는다. */
private class RecordingLockHistoryDao : LockHistoryDao {
    val inserted = mutableListOf<LockHistoryEntity>()

    override suspend fun insert(entity: LockHistoryEntity) {
        inserted += entity
    }

    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> =
        emptyFlow()

    override fun fetchAll(): Flow<List<LockHistoryEntity>> = emptyFlow()

    override suspend fun countSuccessfulSessions(): Int = inserted.size

    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int =
        inserted.count { it.endTimestamp >= timestampMillis }
}

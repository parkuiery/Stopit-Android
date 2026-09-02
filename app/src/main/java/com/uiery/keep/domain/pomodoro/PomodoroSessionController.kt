package com.uiery.keep.domain.pomodoro

import com.uiery.keep.analytics.AnalyticsPomodoroEndReason
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.PomodoroAnalyticsBuckets
import com.uiery.keep.data.lock.TimedLockStartOrigin
import com.uiery.keep.data.lock.TimedLockStartResult
import com.uiery.keep.data.lock.TimedLockStarter
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PomodoroSessionStore
import com.uiery.keep.service.LockHistoryRecorder
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 세션의 시작·진행·종료를 한 곳에서 처리한다.
 *
 * 저장, 구간 전환 계측, 오늘 집중 횟수 누적이 여기서만 일어나게 해서 화면과 포그라운드 서비스가
 * 각자 다른 규칙으로 세션을 움직이지 못하게 한다.
 *
 * **이 컨트롤러는 잠금을 스스로 만들지 않는다.** 휴식 중에도 차단을 유지하므로 집중 세션은 잠금
 * 관점에서 연속된 타이머 잠금 하나이고, 그 잠금은 [TimedLockStarter] 가 만든다. 그래서 잠금 기록,
 * `lock_session_*`, 첫 잠금 전달이 기존 경로 그대로 따라온다. 여기가 더하는 것은 그 위에 얹힌
 * 페이즈 전환과 페이즈 계측뿐이다.
 */
/** 세션 시작 결과. 잠금을 세우지 못하면 세션도 시작되지 않는다. */
internal sealed interface PomodoroStartResult {
    data class Started(val session: PomodoroSession) : PomodoroStartResult

    /** 이미 다른 타이머 잠금이 돌고 있거나 막을 대상이 없다. */
    data class LockUnavailable(val reason: TimedLockStartResult) : PomodoroStartResult
}

@Singleton
internal class PomodoroSessionController
    @Inject
    constructor(
        private val store: PomodoroSessionStore,
        private val analytics: KeepAnalytics,
        private val timedLockStarter: TimedLockStarter,
        private val blockingStateStore: BlockingStateStore,
        private val lockHistoryRecorder: LockHistoryRecorder,
    ) {

        /**
         * 세션을 시작한다. 잠금은 세션 전체 길이만큼 한 번에 예약된다 — 사용자가 실제로 거는 것이
         * `집중 25분`이 아니라 `2시간 10분`이기 때문이다.
         *
         * 타이머 잠금이 이미 돌고 있으면 [PomodoroStartResult.LockUnavailable] 로 거절한다. 두
         * 잠금이 같은 `LOCK_TIME` 을 두고 다투면 어느 쪽 deadline 이 이기는지 알 수 없게 된다.
         */
        suspend fun start(
            cycle: PomodoroCycle,
            entrySurface: String,
            selectedPackages: Set<String>,
            hasWebTargets: Boolean,
            now: Instant,
        ): PomodoroStartResult {
            val totalMinutes = PomodoroPolicy.totalDuration(cycle).toMinutes()
            val lockResult = timedLockStarter.start(
                packages = selectedPackages,
                durationMinutes = totalMinutes,
                origin = TimedLockStartOrigin.Pomodoro,
                hasWebTargets = hasWebTargets,
            )
            if (lockResult !is TimedLockStartResult.Started) {
                return PomodoroStartResult.LockUnavailable(lockResult)
            }

            val session = PomodoroPolicy.start(cycle = cycle, now = now)
            store.save(session)
            // 다음에 다시 쓸 때 고르는 과정을 통째로 건너뛰게 한다. 이 카테고리의 기본 요건이
            // "1–2탭 안에 시작"인데, 매번 사이클을 다시 고르면 그 안에 들어갈 수 없다.
            store.saveLastCycle(cycle)
            timedLockStarter.commit(lockResult)
            analytics.trackPomodoroSessionStarted(
                preset = cycle.kind.analyticsKey,
                entrySurface = entrySurface,
                selectedAppCountBucket = PomodoroAnalyticsBuckets.selectedAppCountBucket(
                    selectedPackages.size,
                ),
                // 커스텀은 preset enum 으로 길이를 알 수 없다. 원본 분 값 대신 bucket 만 보낸다.
                focusMinutesBucket = PomodoroAnalyticsBuckets.focusMinutesBucket(cycle.focusMinutes),
                cycleCountBucket = PomodoroAnalyticsBuckets.cycleCountBucket(cycle.cycles),
            )
            return PomodoroStartResult.Started(session)
        }

        /**
         * 저장된 세션을 [now] 기준으로 따라잡고, 그 사이에 넘어간 구간을 계측한다.
         *
         * 포그라운드 서비스가 살아 있는 동안에는 전환이 하나씩 잡히고, 프로세스가 죽어 있었다면
         * 한 번에 여러 구간이 지나 있을 수 있다.
         */
        suspend fun sync(
            now: Instant,
            zone: ZoneId = ZoneId.systemDefault(),
        ): PomodoroSession? {
            val stored = store.readStoredSession() ?: return null
            if (stored.status.isFinished) return stored

            val resolved = PomodoroPolicy.resolve(session = stored, now = now)
            if (resolved == stored) return stored

            store.save(resolved)

            val newlyCompletedFocus = resolved.completedFocusCount - stored.completedFocusCount
            if (newlyCompletedFocus > 0) {
                // N번째로 완료된 집중의 사이클 번호는 언제나 N이다. 두 값이 같은 순서로 하나씩
                // 올라가기 때문에, 지나간 구간을 되짚지 않아도 번호를 복원할 수 있다.
                for (cycleIndex in (stored.completedFocusCount + 1)..resolved.completedFocusCount) {
                    analytics.trackPomodoroFocusCompleted(
                        preset = resolved.cycle.kind.analyticsKey,
                        cycleIndexBucket = PomodoroAnalyticsBuckets.cycleIndexBucket(cycleIndex),
                    )
                }
                store.addTodayFocusCount(
                    delta = newlyCompletedFocus,
                    today = now.atZone(zone).toLocalDate(),
                )
            }

            // 지나간 휴식이 여러 개여도 지금 들어선 것 하나만 기록한다. 자는 동안 흘러간 휴식은
            // 사용자가 시작한 적이 없다.
            if (resolved.phase.isBreak && resolved.phase != stored.phase) {
                resolved.phase.breakTypeAnalyticsKey?.let { breakType ->
                    analytics.trackPomodoroBreakStarted(
                        preset = resolved.cycle.kind.analyticsKey,
                        breakType = breakType,
                    )
                }
            }

            if (resolved.status == PomodoroSessionStatus.Completed) {
                releaseLock(session = resolved, now = now)
                trackSessionEnded(
                    session = resolved,
                    endReason = completionReason(session = resolved, now = now),
                    now = now,
                )
            }

            return resolved
        }

        /** 사용자가 직접 끝낸 경우. 지금까지 완료한 집중 횟수는 그대로 기록에 남는다. */
        suspend fun endByUser(now: Instant, zone: ZoneId = ZoneId.systemDefault()): PomodoroSession? {
            val current = sync(now = now, zone = zone) ?: return null
            if (current.status.isFinished) return current

            val ended = PomodoroPolicy.endEarly(current)
            store.save(ended)
            // 세션을 끊으면 잠금도 함께 끝나야 한다. `LOCK_TIME` 을 남겨 두면 세션은 끝났는데
            // 타이머 잠금만 예약된 시각까지 계속 도는, 사용자가 종료할 수 없는 상태가 된다.
            releaseLock(session = ended, now = now)
            trackSessionEnded(
                session = ended,
                endReason = AnalyticsPomodoroEndReason.USER_ENDED,
                now = now,
            )
            return ended
        }

        /** 끝난 세션을 저장소에서 치운다. 완료 화면을 닫을 때 호출한다. */
        suspend fun blockDuringBreaks(): Boolean = store.readBlockDuringBreaks()

        suspend fun setBlockDuringBreaks(enabled: Boolean) = store.setBlockDuringBreaks(enabled)

        /** 마지막으로 고른 사이클. 없으면 기본 프리셋. */
        suspend fun lastCycleOrDefault(): PomodoroCycle = store.readLastCycle() ?: PomodoroCycle.Default

        suspend fun clearFinishedSession() {
            val stored = store.readStoredSession() ?: return
            if (!stored.status.isFinished) return
            store.clearSession()
        }

        suspend fun todayFocusCount(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Int =
            store.readTodayFocusCount(now.atZone(zone).toLocalDate())

        /**
         * 세션이 끝났을 때 잠금을 내리고 기록을 남긴다.
         *
         * 잠금 기록은 세션 시작부터 지금까지의 실제 구간이다. 예약된 전체 길이가 아니라 실제로
         * 잠겨 있던 시간을 남겨야 성과 리포트와 완주율이 사실과 맞는다.
         */
        private suspend fun releaseLock(session: PomodoroSession, now: Instant) {
            val lockedApps = blockingStateStore.readSelectedAppPackages()
            blockingStateStore.clearTimedLockSession()
            lockHistoryRecorder.recordSession(
                startTimestamp = session.startedAt.toEpochMilli(),
                endTimestamp = now.toEpochMilli(),
                lockedApps = lockedApps,
                isRoutine = false,
            )
        }

        private suspend fun trackSessionEnded(
            session: PomodoroSession,
            endReason: String,
            now: Instant,
        ) {
            val elapsedMinutes = Duration.between(session.startedAt, now).toMinutes().coerceAtLeast(0)
            analytics.trackPomodoroSessionEnded(
                preset = session.cycle.kind.analyticsKey,
                endReason = endReason,
                completedFocusCountBucket = PomodoroAnalyticsBuckets.completedFocusCountBucket(
                    session.completedFocusCount,
                ),
                elapsedMinutesBucket = PomodoroAnalyticsBuckets.elapsedMinutesBucket(elapsedMinutes),
            )
        }

        /**
         * 살아서 끝난 세션과, 앱이 죽어 있는 동안 끝나 있던 세션을 구분한다.
         *
         * 서비스가 붙어 있으면 초 단위로 따라잡으므로 완료를 곧바로 발견한다. 한참 뒤에 발견했다면
         * 사용자는 그 완료를 보지 못한 것이고, 완주율을 그 둘로 섞어 읽으면 안 된다.
         */
        private fun completionReason(session: PomodoroSession, now: Instant): String {
            val lateness = Duration.between(session.phaseDeadline, now)
            return if (lateness > LIVE_COMPLETION_GRACE) {
                AnalyticsPomodoroEndReason.EXPIRED_RECOVERY
            } else {
                AnalyticsPomodoroEndReason.ALL_CYCLES_COMPLETED
            }
        }

        companion object {
            /** 이 안에서 발견된 완료는 사용자가 화면이나 알림에서 실제로 본 것으로 본다. */
            internal val LIVE_COMPLETION_GRACE: Duration = Duration.ofSeconds(60)
        }
    }

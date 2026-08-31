package com.uiery.keep.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.uiery.keep.domain.pomodoro.PomodoroNotificationPhaseTracker
import com.uiery.keep.domain.pomodoro.PomodoroPhase
import com.uiery.keep.domain.pomodoro.PomodoroSessionController
import com.uiery.keep.notification.PomodoroNotificationContent
import com.uiery.keep.notification.PomodoroNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * 집중 세션이 도는 동안만 사는 포그라운드 서비스.
 *
 * 하는 일은 두 가지다. 초 단위로 세션을 따라잡아 저장소에 반영하고(그 쓰기가 접근성 서비스의
 * 차단 판정을 다시 돌린다), 현재 구간과 남은 시간을 알림으로 보여준다.
 *
 * 알림 권한이 없어도 서비스는 돌고 차단도 유지된다. 알림은 편의이지 잠금의 전제가 아니다.
 */
@AndroidEntryPoint
class PomodoroSessionService : Service() {

    @Inject
    internal lateinit var controller: PomodoroSessionController

    @Inject
    lateinit var notificationHelper: PomodoroNotificationHelper

    private val job: Job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val phaseTracker = PomodoroNotificationPhaseTracker()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForegroundWithCurrentSession()
        if (!isTicking) {
            isTicking = true
            scope.launch { tickUntilSessionEnds() }
        }
        // 세션은 사용자가 예약한 시간까지 살아야 한다. 시스템이 죽였다면 다시 붙는다.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        job.cancel()
        super.onDestroy()
        isTicking = false
    }

    /**
     * Android 는 서비스가 시작된 뒤 곧바로 포그라운드로 올라올 것을 요구한다. 세션을 읽어오기
     * 전에 자리부터 잡아야 하므로, 첫 알림은 마지막으로 알려진 구간으로 그린다.
     */
    private fun startInForegroundWithCurrentSession() {
        val placeholder = PomodoroNotificationContent(
            phase = phaseTracker.lastPhase ?: PomodoroPhase.Focus,
            remainingClock = "0:00",
            cycleIndex = 1,
            cyclesPerSession = com.uiery.keep.domain.pomodoro.PomodoroCycle.DEFAULT_CYCLES,
            alerts = false,
        )
        ServiceCompat.startForeground(
            this,
            PomodoroNotificationHelper.POMODORO_NOTIFICATION_ID,
            notificationHelper.buildSessionNotification(placeholder),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private suspend fun tickUntilSessionEnds() {
        while (scope.isActive) {
            val now = Instant.now()
            val session = controller.sync(now = now)
            if (session == null || session.status.isFinished) {
                notificationHelper.cancelSession()
                stopSelf()
                return
            }
            notificationHelper.notifySession(
                PomodoroNotificationContent.from(
                    session = session,
                    now = now,
                    previousPhase = phaseTracker.lastPhase,
                ),
            )
            phaseTracker.record(session.phase)
            delay(TICK_INTERVAL_MILLIS)
        }
    }

    companion object {
        private const val TICK_INTERVAL_MILLIS = 1_000L

        @Volatile
        private var isTicking = false

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PomodoroSessionService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PomodoroSessionService::class.java))
        }
    }
}

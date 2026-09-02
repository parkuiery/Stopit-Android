package com.uiery.keep.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.datastore.RoutineStore
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.domain.pomodoro.PomodoroBlockContextSource
import com.uiery.keep.domain.pomodoro.PomodoroSessionRestorePolicy
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingLauncher
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingTrigger
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.service.PomodoroSessionService
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var routineScheduler: RoutineScheduler

    @Inject
    lateinit var routineRepository: RoutineRepository

    @Inject
    @KeepDataSource
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    lateinit var routineWebsiteBlockingLauncher: RoutineWebsiteBlockingLauncher

    @Inject
    lateinit var pomodoroBlockContextSource: PomodoroBlockContextSource

    override fun onReceive(context: Context, intent: Intent) {
        if (!RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(intent.action)) {
            return
        }

        val pendingResult = goAsync()
        ReceiverCoroutineRunner.launch(
            receiverName = "BootReceiver",
            finish = { pendingResult.finish() },
        ) {
            restorePomodoroSessionForBoot(context, intent.action)
            restoreRoutinesForBoot(intent.action)
        }
    }

    /**
     * 재부팅 뒤 진행 중인 세션의 서비스를 다시 세운다.
     *
     * 차단은 저장된 deadline 으로 이어지므로 이것이 없어도 앱은 계속 막힌다. 되살리는 것은
     * **진행 알림과 구간 갱신**이다 — 없으면 사용자는 세션이 도는지 앱을 열어야 알 수 있고,
     * 저장된 구간도 앱을 열기 전까지 멈춰 있다.
     *
     * 루틴 재예약보다 **먼저** 부른다. BOOT_COMPLETED 의 포그라운드 서비스 시작 면제는 방송을
     * 처리하는 동안에만 열려 있고, 루틴이 많으면 아래 재예약 루프가 그 창을 다 쓴다.
     */
    suspend fun restorePomodoroSessionForBoot(context: Context, action: String?) {
        if (!PomodoroSessionRestorePolicy.shouldRestartSessionService(action)) {
            return
        }
        // 살아 있는 세션이 없으면 시작하지 않는다. 곧바로 스스로 멈출 서비스를 포그라운드로
        // 올렸다 내리는 일을 만들지 않는다.
        if (pomodoroBlockContextSource.blockContext(Instant.now()) == null) {
            return
        }
        PomodoroSessionService.start(context)
    }

    suspend fun restoreRoutinesForBoot(action: String?) {
        if (!RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(action)) {
            return
        }

        val routineStore = RoutineStore(dataStore)
        val storedRoutines = routineStore.readCachedRoutines()
        val databaseRoutines = routineRepository.fetchAllOnce()
        var routines = RoutineReceiverPolicy.resolveRoutines(
            storedRoutines = storedRoutines,
            databaseRoutines = databaseRoutines,
        )

        // 알람은 창의 시작에만 걸려 있다. 재부팅이 창 한가운데였다면 이미 지나간 시작을
        // 아무도 다시 알려주지 않으므로, 여기서 직접 판정해야 그 회차가 살아난다.
        //
        // BOOT_COMPLETED 는 백그라운드 포그라운드-서비스 시작 제한의 면제 대상이지만 그
        // 면제는 방송을 처리하는 동안에만 열려 있다. 루틴이 많으면 아래 재예약 루프가
        // 길어지므로 그 전에 시작한다.
        routineWebsiteBlockingLauncher.apply(routines, RoutineWebsiteBlockingTrigger.BOOT)

        var updatedRoutines = routines
        val disabledRoutineIds = linkedSetOf<Long>()
        var shouldResetAlarmPermissionPrompt = false
        routines.filter { it.isEnabled }.forEach { routine ->
            val scheduleApplication = RoutineReceiverPolicy.applyScheduleResult(
                routines = updatedRoutines,
                routineId = routine.id,
                scheduleResult = routineScheduler.scheduleRoutine(routine),
            )
            updatedRoutines = scheduleApplication.routines
            disabledRoutineIds += scheduleApplication.disabledRoutineIds
            shouldResetAlarmPermissionPrompt =
                shouldResetAlarmPermissionPrompt || scheduleApplication.shouldResetAlarmPermissionPrompt
        }

        disabledRoutineIds.forEach { routineId ->
            routineRepository.updateIsEnabledById(routineId, false)
        }

        if (RoutineReceiverPolicy.shouldRewriteCompatibilityCache(
                storedRoutines = storedRoutines,
                databaseRoutines = databaseRoutines,
                updatedRoutines = updatedRoutines,
            )
        ) {
            routineStore.writeCachedRoutines(updatedRoutines)
        }

        if (shouldResetAlarmPermissionPrompt) {
            RoutineNoticeStore(dataStore).resetAlarmPermissionPrompt()
        }
    }
}

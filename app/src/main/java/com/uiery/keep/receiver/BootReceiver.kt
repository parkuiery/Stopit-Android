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
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingLauncher
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingTrigger
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onReceive(context: Context, intent: Intent) {
        if (!RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(intent.action)) {
            return
        }

        val pendingResult = goAsync()
        ReceiverCoroutineRunner.launch(
            receiverName = "BootReceiver",
            finish = { pendingResult.finish() },
        ) {
            restoreRoutinesForBoot(intent.action)
        }
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

package com.uiery.keep.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.uiery.keep.KeepDataSource
import com.uiery.keep.R
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.datastore.RoutineStore
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingPolicy
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteWindow
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.NotificationHelper
import com.uiery.keep.util.AppLogger
import com.uiery.keep.util.currentRoutineWindowEndDateTime
import com.uiery.keep.util.isRoutineActiveNow
import com.uiery.keep.util.toDayOfWeekList
import com.uiery.keep.websiteblocking.KeepDnsVpnService
import com.uiery.keep.websiteblocking.WebsiteBlockingRuntimeState
import com.uiery.keep.websiteblocking.WebsiteBlockingStatus
import java.time.LocalDateTime
import java.time.ZoneId
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.notification.RoutineStartNotificationResult
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@AndroidEntryPoint
class RoutineAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var routineScheduler: RoutineScheduler

    @Inject
    lateinit var routineRepository: RoutineRepository

    @Inject
    @KeepDataSource
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val routineName = intent.getStringExtra(EXTRA_ROUTINE_NAME)
        val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, -1L)

        RoutineReceiverPolicy.parseRoutineAlarmTrigger(
            action = action,
            routineName = routineName,
            routineId = routineId,
        ) ?: return

        val pendingResult = goAsync()
        ReceiverCoroutineRunner.launch(
            receiverName = "RoutineAlarmReceiver",
            finish = { pendingResult.finish() },
        ) {
            handleRoutineAlarm(
                action = action,
                routineName = routineName,
                routineId = routineId,
            )
        }
    }

    suspend fun handleRoutineAlarm(
        action: String?,
        routineName: String?,
        routineId: Long,
    ) {
        val trigger = RoutineReceiverPolicy.parseRoutineAlarmTrigger(
            action = action,
            routineName = routineName,
            routineId = routineId,
        ) ?: return

        val routineStore = RoutineStore(dataStore)
        val storedRoutines = routineStore.readCachedRoutines()
        val databaseRoutines = routineRepository.fetchAllOnce()
        val routines = RoutineReceiverPolicy.resolveRoutines(
            storedRoutines = storedRoutines,
            databaseRoutines = databaseRoutines,
        )

        val routineToReschedule = RoutineReceiverPolicy.findEnabledRoutineToReschedule(
            routines = routines,
            routineId = trigger.routineId,
        )

        if (RoutineReceiverPolicy.shouldShowRoutineStartNotice(
                routines = routines,
                routineId = trigger.routineId,
            )
        ) {
            val notificationResult = notificationHelper.showRoutineStartNotification(
                trigger.routineName,
                trigger.routineId,
            )
            RoutineReceiverPolicy.buildPendingRoutineStartNotice(
                notificationResult = notificationResult,
                fallbackMessage = dataStoreFallbackMessage(
                    routineName = trigger.routineName,
                    notificationResult = notificationResult,
                ),
            )?.let { pendingNotice ->
                RoutineNoticeStore(dataStore).enqueuePendingRoutineStartNotice(pendingNotice)
            }
        }

        var updatedRoutines = routines
        val disabledRoutineIds = linkedSetOf<Long>()
        var shouldResetAlarmPermissionPrompt = false

        routineToReschedule?.let { routine ->
            val scheduleApplication = RoutineReceiverPolicy.applyRoutineAlarmRescheduleResult(
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

        applyRoutineWebsiteBlocking(updatedRoutines)
    }

    /**
     * 루틴은 화면 없이 시작된다. 여기서 시작하지 않으면 웹사이트는 사용자가 앱을 열 때까지
     * 열려 있고, 루틴이 앱만 막는다는 사실을 아무도 말해주지 않는다.
     *
     * 시스템 VPN 동의창은 여기서 띄울 수 없으므로, 동의가 없으면 웹 차단만 조용히 건너뛰고
     * 상태를 남긴다. 화면이 열릴 때 배너가 그 사실을 설명한다.
     */
    private fun applyRoutineWebsiteBlocking(routines: List<RoutineModel>) {
        val windows = routines.toWebsiteWindows()
        val session = RoutineWebsiteBlockingPolicy.resolveSession(windows)
        AppLogger.debug(
            DIAGNOSTIC_TAG,
            "routine_web total=${windows.size}" +
                " active=${windows.count { it.isEnabled && it.isActiveNow }}" +
                " withSites=${windows.count { it.websites.isNotEmpty() }}" +
                " session=${session != null}" +
                " consent=${VpnService.prepare(appContext) == null}",
        )
        if (session == null) {
            if (WebsiteBlockingRuntimeState.status.value != WebsiteBlockingStatus.Inactive) {
                appContext.startService(KeepDnsVpnService.stopIntent(appContext))
                WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Inactive)
            }
            return
        }

        if (VpnService.prepare(appContext) != null) {
            WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.ConsentDenied)
            return
        }

        ContextCompat.startForegroundService(
            appContext,
            KeepDnsVpnService.startIntent(
                context = appContext,
                domains = session.domains,
                stopAtEpochMillis = session.stopAtEpochMillis,
            ),
        )
    }

    private fun List<RoutineModel>.toWebsiteWindows(
        nowDateTime: LocalDateTime = LocalDateTime.now(),
    ): List<RoutineWebsiteWindow> = map { routine ->
        RoutineWebsiteWindow(
            isEnabled = routine.isEnabled,
            isActiveNow = isRoutineActiveNow(
                startTime = routine.startTime,
                endTime = routine.endTime,
                repeatDays = routine.repeatDays.toDayOfWeekList(),
                nowDateTime = nowDateTime,
            ),
            endEpochMillis = currentRoutineWindowEndDateTime(
                startTime = routine.startTime,
                endTime = routine.endTime,
                nowDateTime = nowDateTime,
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            websites = routine.lockWebsites.orEmpty().toSet(),
        )
    }

    private fun dataStoreFallbackMessage(
        routineName: String,
        notificationResult: RoutineStartNotificationResult,
    ): String = RoutineReceiverPolicy.selectRoutineStartFallbackMessage(
        notificationResult = notificationResult,
        permissionDeniedMessage = appContext.getString(
            R.string.routine_notification_permission_fallback_message,
            routineName,
        ),
        channelDisabledMessage = appContext.getString(
            R.string.routine_notification_channel_disabled_fallback_message,
            routineName,
        ),
    )

    companion object {
        private const val DIAGNOSTIC_TAG = "KeepRoutineWeb"
        const val EXTRA_ROUTINE_NAME = "extra_routine_name"
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
        const val ACTION_ROUTINE_ALARM = "com.uiery.keep.ACTION_ROUTINE_ALARM"
    }
}

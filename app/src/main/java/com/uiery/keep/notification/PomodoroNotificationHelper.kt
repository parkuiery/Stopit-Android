package com.uiery.keep.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.uiery.keep.MainActivity
import com.uiery.keep.R
import com.uiery.keep.domain.pomodoro.PomodoroPhase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 집중 세션이 도는 동안 유지되는 진행 알림.
 *
 * 루틴 채널을 재사용하지 않는다. 사용자가 루틴 알림을 끄면 세션 알림까지 사라지고, 그 반대도
 * 마찬가지다. 두 알림은 목적이 다르므로 채널도 따로 둔다.
 *
 * 채널 importance 는 `DEFAULT` 다. 구간 전환은 알려야 하지만 heads-up 으로 화면을 덮을 일은 아니다.
 */
@Singleton
class PomodoroNotificationHelper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val notificationManager = NotificationManagerCompat.from(context)

        init {
            createChannel()
        }

        private fun createChannel() {
            val channel = NotificationChannel(
                POMODORO_CHANNEL_ID,
                context.getString(R.string.notification_channel_pomodoro_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_pomodoro_description)
                setShowBadge(false)
            }
            val systemNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }

        internal fun buildSessionNotification(content: PomodoroNotificationContent): Notification =
            NotificationCompat.Builder(context, POMODORO_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_stopit)
                .setColor(Color.White.toArgb())
                .setContentTitle(context.getString(content.phase.titleResId()))
                .setContentText(
                    context.getString(
                        R.string.notification_pomodoro_progress,
                        content.remainingClock,
                        content.cycleIndex,
                        content.cyclesPerSession,
                    ),
                )
                // 잠금이 도는 동안 사용자가 지울 수 있으면 진행 상태를 잃는다. 세션이 끝나면
                // 서비스가 직접 내린다.
                .setOngoing(true)
                .setSilent(!content.alerts)
                .setOnlyAlertOnce(!content.alerts)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(sessionContentIntent())
                .build()

        internal fun notifySession(content: PomodoroNotificationContent) {
            if (!notificationManager.areNotificationsEnabled()) return
            notificationManager.notify(POMODORO_NOTIFICATION_ID, buildSessionNotification(content))
        }

        internal fun cancelSession() {
            notificationManager.cancel(POMODORO_NOTIFICATION_ID)
        }

        private fun sessionContentIntent(): PendingIntent = PendingIntent.getActivity(
            context,
            POMODORO_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_POMODORO_NOTIFICATION_TAP
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        companion object {
            internal const val POMODORO_CHANNEL_ID = "POMODORO_CHANNEL"
            internal const val POMODORO_NOTIFICATION_ID = 8_301
            internal const val ACTION_POMODORO_NOTIFICATION_TAP =
                "com.uiery.keep.ACTION_POMODORO_NOTIFICATION_TAP"
        }
    }

internal fun PomodoroPhase.titleResId(): Int = when (this) {
    PomodoroPhase.Focus -> R.string.notification_pomodoro_focus_title
    PomodoroPhase.ShortBreak -> R.string.notification_pomodoro_short_break_title
    PomodoroPhase.LongBreak -> R.string.notification_pomodoro_long_break_title
}

package com.uiery.keep.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.uiery.keep.MainActivity
import com.uiery.keep.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RoutineStartNotificationResult {
    data object Posted : RoutineStartNotificationResult
    data object PermissionDenied : RoutineStartNotificationResult
    data object ChannelDisabled : RoutineStartNotificationResult
}

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            ROUTINE_CHANNEL_ID,
            context.getString(R.string.notification_channel_routine_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_routine_description)
        }

        val systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemNotificationManager.createNotificationChannel(channel)
    }

    fun showRoutineStartNotification(routineName: String, routineId: Long): RoutineStartNotificationResult {
        if (!notificationManager.areNotificationsEnabled()) {
            return RoutineStartNotificationResult.PermissionDenied
        }

        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return RoutineStartNotificationResult.PermissionDenied
        }

        if (!isRoutineChannelEnabled()) {
            return RoutineStartNotificationResult.ChannelDisabled
        }

        val notification = buildRoutineStartNotification(routineName, routineId)

        notificationManager.notify(RoutineIdentifierPolicy.routineStartNotificationId(routineId), notification)
        return RoutineStartNotificationResult.Posted
    }

    fun buildRoutineStartNotification(routineName: String, routineId: Long) =
        NotificationCompat.Builder(context, ROUTINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setColor(Color.White.toArgb())
            .setContentTitle(
                context.getString(R.string.notification_routine_start_title, routineName)
            )
            .setContentText(context.getString(R.string.notification_routine_start_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(routineStartContentIntent(routineId))
            .setAutoCancel(true)
            .build()

    fun buildRoutineStartNotificationTapIntent(routineId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_ROUTINE_START_NOTIFICATION_TAP
            putExtra(EXTRA_ROUTINE_ID, routineId)
            putExtra(EXTRA_NOTIFICATION_SOURCE, NOTIFICATION_SOURCE_ROUTINE_START)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    private fun routineStartContentIntent(routineId: Long): PendingIntent {
        return PendingIntent.getActivity(
            context,
            RoutineIdentifierPolicy.routineStartNotificationId(routineId),
            buildRoutineStartNotificationTapIntent(routineId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        internal const val ROUTINE_CHANNEL_ID = "ROUTINE_CHANNEL"
        internal const val ACTION_ROUTINE_START_NOTIFICATION_TAP =
            "com.uiery.keep.ACTION_ROUTINE_START_NOTIFICATION_TAP"
        internal const val EXTRA_ROUTINE_ID = "extra_routine_id"
        internal const val EXTRA_NOTIFICATION_SOURCE = "extra_notification_source"
        internal const val NOTIFICATION_SOURCE_ROUTINE_START = "routine_start"
    }

    private fun isRoutineChannelEnabled(): Boolean {
        val systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return systemNotificationManager.getNotificationChannel(ROUTINE_CHANNEL_ID)?.importance !=
            NotificationManager.IMPORTANCE_NONE
    }
}

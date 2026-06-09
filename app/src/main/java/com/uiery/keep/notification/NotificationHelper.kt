package com.uiery.keep.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
            NotificationManager.IMPORTANCE_HIGH
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

        val notification = NotificationCompat.Builder(context, ROUTINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setColor(Color.White.toArgb())
            .setContentTitle(
                context.getString(R.string.notification_routine_start_title, routineName)
            )
            .setContentText(context.getString(R.string.notification_routine_start_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(RoutineIdentifierPolicy.routineStartNotificationId(routineId), notification)
        return RoutineStartNotificationResult.Posted
    }

    companion object {
        internal const val ROUTINE_CHANNEL_ID = "ROUTINE_CHANNEL"
    }

    private fun isRoutineChannelEnabled(): Boolean {
        val systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return systemNotificationManager.getNotificationChannel(ROUTINE_CHANNEL_ID)?.importance !=
            NotificationManager.IMPORTANCE_NONE
    }
}

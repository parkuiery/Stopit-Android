package com.uiery.keep.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.uiery.keep.R
import com.uiery.keep.util.formatMinuteSecondCountdown
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val EMERGENCY_UNLOCK_NOTIFICATION_CHANNEL_ID = "emergency_unlock"
private const val EMERGENCY_UNLOCK_NOTIFICATION_ID = 9001

internal fun cancelEmergencyUnlockNotification(context: Context) {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(EMERGENCY_UNLOCK_NOTIFICATION_ID)
}

@Singleton
class EmergencyUnlockNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        internal const val CHANNEL_ID = EMERGENCY_UNLOCK_NOTIFICATION_CHANNEL_ID
        internal const val NOTIFICATION_ID = EMERGENCY_UNLOCK_NOTIFICATION_ID
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.emergency_unlock_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.emergency_unlock_notification_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    internal fun showCountdown(remainingSeconds: Int, totalSeconds: Int): EmergencyUnlockNotificationPostResult {
        if (!canPostNotification()) {
            cancel()
            return EmergencyUnlockNotificationPostResult.PermissionDenied
        }
        val timeText = formatMinuteSecondCountdown(remainingSeconds)
        val progress = if (totalSeconds > 0) (remainingSeconds * 100) / totalSeconds else 0

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.kepp_icon)
            .setContentTitle(context.getString(R.string.emergency_unlock_active))
            .setContentText(
                context.getString(R.string.emergency_unlock_remaining_time, timeText)
            )
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        return EmergencyUnlockNotificationPostResult.Posted
    }

    internal fun showExpired(): EmergencyUnlockNotificationPostResult {
        if (!canPostNotification()) {
            cancel()
            return EmergencyUnlockNotificationPostResult.PermissionDenied
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.kepp_icon)
            .setContentTitle(context.getString(R.string.emergency_unlock_expired))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        return EmergencyUnlockNotificationPostResult.Posted
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun canPostNotification(): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val permissionGranted =
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return resolveEmergencyUnlockNotificationPostResult(
            notificationsEnabled = notificationsEnabled,
            postNotificationsPermissionGranted = permissionGranted,
        ) == EmergencyUnlockNotificationPostResult.Posted
    }
}

package com.uiery.keep.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.uiery.keep.R
import com.uiery.keep.util.formatMinuteSecondCountdown
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyUnlockNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val CHANNEL_ID = "emergency_unlock"
        private const val NOTIFICATION_ID = 9001
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

    fun showCountdown(remainingSeconds: Int, totalSeconds: Int) {
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
    }

    fun showExpired() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.kepp_icon)
            .setContentTitle(context.getString(R.string.emergency_unlock_expired))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}

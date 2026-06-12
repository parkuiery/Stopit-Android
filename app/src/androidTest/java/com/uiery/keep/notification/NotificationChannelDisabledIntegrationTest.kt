package com.uiery.keep.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.service.EmergencyUnlockNotificationHelper
import com.uiery.keep.service.EmergencyUnlockNotificationPostResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationChannelDisabledIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setUp() {
        grantPostNotificationsPermission()
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
        notificationManager.deleteNotificationChannel(NotificationHelper.ROUTINE_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(EmergencyUnlockNotificationHelper.CHANNEL_ID)
        NotificationHelper(context)
        EmergencyUnlockNotificationHelper(context)
    }

    @Test
    fun routineStartNotificationReturnsChannelDisabledAndDoesNotPostWhenRoutineChannelImportanceNone() {
        disableNotificationChannel(NotificationHelper.ROUTINE_CHANNEL_ID)

        val result = NotificationHelper(context).showRoutineStartNotification(
            routineName = "Morning focus",
            routineId = ROUTINE_NOTIFICATION_ID.toLong(),
        )

        assertEquals(RoutineStartNotificationResult.ChannelDisabled, result)
        assertFalse(activeNotificationIds().contains(ROUTINE_NOTIFICATION_ID))
    }

    @Test
    fun emergencyUnlockCountdownReturnsChannelDisabledAndDoesNotPostWhenChannelImportanceNone() {
        disableNotificationChannel(EmergencyUnlockNotificationHelper.CHANNEL_ID)

        val result = EmergencyUnlockNotificationHelper(context).showCountdown(
            remainingSeconds = 60,
            totalSeconds = 60,
        )

        assertEquals(EmergencyUnlockNotificationPostResult.ChannelDisabled, result)
        assertFalse(activeNotificationIds().contains(EmergencyUnlockNotificationHelper.NOTIFICATION_ID))
    }

    private fun grantPostNotificationsPermission() {
        instrumentation.uiAutomation.executeShellCommand(
            "pm clear-permission-flags ${context.packageName} android.permission.POST_NOTIFICATIONS user-set user-fixed",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} POST_NOTIFICATION allow",
        ).close()
        waitUntil("POST_NOTIFICATIONS should be enabled for channel-disabled notification tests") {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun disableNotificationChannel(channelId: String) {
        notificationManager.deleteNotificationChannel(channelId)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Disabled test channel",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            notificationManager.getNotificationChannel(channelId)?.importance,
        )
    }

    private fun activeNotificationIds(): Set<Int> =
        notificationManager.activeNotifications.map { it.id }.toSet()

    private fun waitUntil(
        message: String,
        timeoutMillis: Long = 5_000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100L)
        }
        throw AssertionError(message)
    }

    private companion object {
        private const val ROUTINE_NOTIFICATION_ID = 556
    }
}

package com.uiery.keep.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.R
import com.uiery.keep.service.EmergencyUnlockNotificationHelper
import com.uiery.keep.service.EmergencyUnlockNotificationPostResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSmallIconIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setUp() {
        grantPostNotificationsPermission()
        notificationManager.cancelAll()
        NotificationHelper(context)
        EmergencyUnlockNotificationHelper(context)
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
    }

    @Test
    fun routineStartNotificationPostsDedicatedSmallIconResource() {
        val routineId = 952L

        val result = NotificationHelper(context).showRoutineStartNotification(
            routineName = "Small icon QA",
            routineId = routineId,
        )

        assertEquals(RoutineStartNotificationResult.Posted, result)
        assertNotificationSmallIcon(
            notificationId = RoutineIdentifierPolicy.routineStartNotificationId(routineId),
        )
    }

    @Test
    fun emergencyUnlockCountdownNotificationPostsDedicatedSmallIconResource() {
        val result = EmergencyUnlockNotificationHelper(context).showCountdown(
            remainingSeconds = 60,
            totalSeconds = 60,
        )

        assertEquals(EmergencyUnlockNotificationPostResult.Posted, result)
        assertNotificationSmallIcon(EmergencyUnlockNotificationHelper.NOTIFICATION_ID)
    }

    @Test
    fun emergencyUnlockExpiredNotificationPostsDedicatedSmallIconResource() {
        val result = EmergencyUnlockNotificationHelper(context).showExpired()

        assertEquals(EmergencyUnlockNotificationPostResult.Posted, result)
        assertNotificationSmallIcon(EmergencyUnlockNotificationHelper.NOTIFICATION_ID)
    }

    private fun assertNotificationSmallIcon(notificationId: Int) {
        val notification = waitForNotification(notificationId)
        assertEquals(
            "Notification smallIcon must use the dedicated alpha-mask vector resource",
            R.drawable.ic_notification_stopit,
            notification.smallIcon.resId,
        )
    }

    private fun waitForNotification(notificationId: Int): Notification {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val notification = notificationManager.activeNotifications
                .firstOrNull { it.id == notificationId }
                ?.notification
            if (notification != null) {
                return notification
            }
            Thread.sleep(50)
        }
        throw AssertionError("Expected active notification id=$notificationId")
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
        waitUntil("POST_NOTIFICATIONS should be enabled for small icon notification tests") {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun waitUntil(message: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(50)
        }
        throw AssertionError(message)
    }
}

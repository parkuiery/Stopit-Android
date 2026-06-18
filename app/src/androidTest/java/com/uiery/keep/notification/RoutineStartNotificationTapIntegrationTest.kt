package com.uiery.keep.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutineStartNotificationTapIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setUp() {
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
        notificationManager.deleteNotificationChannel(NotificationHelper.ROUTINE_CHANNEL_ID)
        NotificationHelper(context)
    }

    @Test
    fun routineStartNotificationBuildsWithTapActionToReturnToApp() {
        val routineId = ROUTINE_NOTIFICATION_ID.toLong()

        val helper = NotificationHelper(context)
        val notification = helper.buildRoutineStartNotification(
            routineName = "Morning focus",
            routineId = routineId,
        )
        assertNotNull(
            "Routine start notifications must include a tap action so users can return to Stopit.",
            notification.contentIntent,
        )

        val tapIntent = helper.buildRoutineStartNotificationTapIntent(routineId)
        assertEquals(
            "Routine-start tap intent should use the routing action constant.",
            NotificationHelper.ACTION_ROUTINE_START_NOTIFICATION_TAP,
            tapIntent.action,
        )
        assertEquals(
            "RoutineId should be carried to the target activity.",
            routineId,
            tapIntent.getLongExtra(NotificationHelper.EXTRA_ROUTINE_ID, -1),
        )
        assertEquals(
            "Routine start source should be included for optional routing telemetry.",
            NotificationHelper.NOTIFICATION_SOURCE_ROUTINE_START,
            tapIntent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_SOURCE),
        )
    }

    @Test
    fun routineStartNotificationShadeTapRoutesToRoutineScreen() {
        val device = UiDevice.getInstance(instrumentation)
        val routineName = "QA963 notification tap"
        val helper = NotificationHelper(context)
        grantPostNotificationsPermission()

        assertEquals(
            "Routine start notification must be posted before notification-shade tap QA can run.",
            RoutineStartNotificationResult.Posted,
            helper.showRoutineStartNotification(
                routineName = routineName,
                routineId = ROUTINE_NOTIFICATION_ID.toLong(),
            ),
        )

        device.pressHome()
        device.openNotification()
        val row = device.wait(Until.findObject(By.textContains(routineName)), UI_TIMEOUT_MS)
        assertNotNull(
            "The real app-posted routine start notification row should be visible in the system shade.",
            row,
        )

        row.click()

        assertTrue(
            "Tapping the real notification row should route back to the Routine screen.",
            device.wait(Until.hasObject(By.textContains("My Routine")), UI_TIMEOUT_MS) ||
                device.wait(Until.hasObject(By.textContains("Add Routine")), UI_TIMEOUT_MS),
        )
    }

    private fun grantPostNotificationsPermission() {
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} POST_NOTIFICATION allow",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "appops set --uid ${context.packageName} POST_NOTIFICATION allow",
        ).close()
        waitUntil("POST_NOTIFICATIONS should be enabled before posting the routine-start notification") {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun waitUntil(message: String, timeoutMs: Long = UI_TIMEOUT_MS, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue(message, condition())
    }

    private companion object {
        private const val ROUTINE_NOTIFICATION_ID = 556
        private const val UI_TIMEOUT_MS = 10_000L
    }
}

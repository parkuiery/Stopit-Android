package com.uiery.keep.notification

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private companion object {
        private const val ROUTINE_NOTIFICATION_ID = 556
    }
}

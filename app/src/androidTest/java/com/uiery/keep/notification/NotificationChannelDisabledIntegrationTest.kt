package com.uiery.keep.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.database.KeepDatabase
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.data.routine.RoomRoutineRepository
import com.uiery.keep.receiver.RoutineAlarmReceiver
import com.uiery.keep.service.EmergencyUnlockNotificationHelper
import com.uiery.keep.service.EmergencyUnlockNotificationPostResult
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class NotificationChannelDisabledIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var database: KeepDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreName: String

    @Before
    fun setUp() {
        dataStoreName = "$DATASTORE_PREFIX-${System.currentTimeMillis()}-${System.nanoTime()}"
        grantPostNotificationsPermission()
        grantExactAlarmPermission()
        notificationManager.cancelAll()
        database = Room.inMemoryDatabaseBuilder(context, KeepDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(dataStoreName) },
        )
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
        notificationManager.deleteNotificationChannel(NotificationHelper.ROUTINE_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(EmergencyUnlockNotificationHelper.CHANNEL_ID)
        database.close()
        deleteDataStoreFiles()
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
    fun routineAlarmReceiverStoresChannelDisabledFallbackCopyWhenRoutineChannelImportanceNone() = runBlocking {
        database.routineDao().insert(enabledRoutineEntity(id = ROUTINE_NOTIFICATION_ID.toLong(), name = "Morning focus"))
        disableNotificationChannel(NotificationHelper.ROUTINE_CHANNEL_ID)
        val receiver = RoutineAlarmReceiver().apply {
            notificationHelper = NotificationHelper(context)
            routineScheduler = RoutineScheduler(context)
            routineRepository = RoomRoutineRepository(database.routineDao())
            dataStore = this@NotificationChannelDisabledIntegrationTest.dataStore
            appContext = context
        }

        receiver.handleRoutineAlarm(
            action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM,
            routineName = "Morning focus",
            routineId = ROUTINE_NOTIFICATION_ID.toLong(),
        )

        val notices = RoutineNoticeStore(dataStore).readPendingRoutineStartNoticeMessages()
        assertEquals(1, notices.size)
        assertTrue(notices.single().contains("notification channel"))
        assertFalse(notices.single().contains("notification permission is off"))
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

    private fun grantExactAlarmPermission() {
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow",
        ).close()
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

    private fun enabledRoutineEntity(id: Long, name: String): RoutineEntity {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val startTotalMinutes = (now.time.hour * 60 + now.time.minute + 10) % (24 * 60)
        val endTotalMinutes = (startTotalMinutes + 30) % (24 * 60)
        return RoutineEntity(
            id = id,
            name = name,
            startTime = LocalTime(hour = startTotalMinutes / 60, minute = startTotalMinutes % 60),
            endTime = LocalTime(hour = endTotalMinutes / 60, minute = endTotalMinutes % 60),
            repeatDays = listOf(today),
            lockApplications = listOf("com.example.blocked"),
            isEnabled = true,
            changeLockHours = null,
        )
    }

    private fun deleteDataStoreFiles() {
        context.preferencesDataStoreFile(dataStoreName).delete()
        context.preferencesDataStoreFile(dataStoreName).parentFile?.listFiles()
            ?.filter { it.name.startsWith(DATASTORE_PREFIX) }
            ?.forEach(File::delete)
    }

    private companion object {
        private const val ROUTINE_NOTIFICATION_ID = 556
        private const val DATASTORE_PREFIX = "notification-channel-disabled"
        private val today: DayOfWeek = java.time.LocalDate.now().dayOfWeek
    }
}

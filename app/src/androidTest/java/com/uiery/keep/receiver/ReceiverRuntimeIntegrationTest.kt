package com.uiery.keep.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.app.NotificationManagerCompat
import com.uiery.keep.R
import com.uiery.keep.database.KeepDatabase
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.NotificationHelper
import com.uiery.keep.notification.RoutineScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class ReceiverRuntimeIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var database: KeepDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreName: String

    @Before
    fun setUp() {
        runBlocking {
            dataStoreName = "$DATASTORE_PREFIX-${System.currentTimeMillis()}-${System.nanoTime()}"
            grantExactAlarmPermission()
            clearAppState()
            database = Room.databaseBuilder(context, KeepDatabase::class.java, DATABASE_NAME)
                .allowMainThreadQueries()
                .build()
            dataStore = createDataStore()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            cancelRoutineAlarm(TEST_ROUTINE_ID)
            cancelNotification(TEST_ROUTINE_ID)
            database.close()
            clearAppState()
        }
    }

    @Test
    fun bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm() = runBlocking {
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Boot restore"))
        val receiver = BootReceiver().apply {
            routineScheduler = RoutineScheduler(context)
            routineDao = database.routineDao()
            dataStore = this@ReceiverRuntimeIntegrationTest.dataStore
        }

        receiver.restoreRoutinesForBoot(Intent.ACTION_BOOT_COMPLETED)

        waitUntil("BootReceiver should persist routines into DataStore") {
            storedRoutineNames() == listOf("Boot restore")
        }
        waitUntil("BootReceiver should schedule the restored routine") {
            findRoutinePendingIntent(TEST_ROUTINE_ID) != null
        }

        assertEquals(listOf("Boot restore"), storedRoutineNames())
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
    }

    @Test
    fun manifestRegistersBootReceiverForPackageAndClockChangeActions() {
        listOf(
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        ).forEach { action ->
            assertTrue(
                "BootReceiver should be registered for $action",
                matchingReceiverClassNames(action).contains(BootReceiver::class.java.name),
            )
        }
    }

    @Test
    fun timeChangedRestoresRoutinesFromRoomAndSchedulesAlarm() = runBlocking {
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Clock changed restore"))
        val receiver = BootReceiver().apply {
            routineScheduler = RoutineScheduler(context)
            routineDao = database.routineDao()
            dataStore = this@ReceiverRuntimeIntegrationTest.dataStore
        }

        receiver.restoreRoutinesForBoot(Intent.ACTION_TIME_CHANGED)

        waitUntil("BootReceiver should persist routines into DataStore after time change") {
            storedRoutineNames() == listOf("Clock changed restore")
        }
        waitUntil("BootReceiver should reschedule restored routine after time change") {
            findRoutinePendingIntent(TEST_ROUTINE_ID) != null
        }

        assertEquals(listOf("Clock changed restore"), storedRoutineNames())
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
    }

    @Test
    fun timezoneChangedRestoresMultiDayRoutinesFromRoomAndSchedulesAlarms() = runBlocking {
        database.routineDao().insert(
            enabledRoutineEntity(
                id = TEST_ROUTINE_ID,
                name = "Timezone changed restore",
                repeatDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            ),
        )
        val receiver = BootReceiver().apply {
            routineScheduler = RoutineScheduler(context)
            routineDao = database.routineDao()
            dataStore = this@ReceiverRuntimeIntegrationTest.dataStore
        }

        receiver.restoreRoutinesForBoot(Intent.ACTION_TIMEZONE_CHANGED)

        waitUntil("BootReceiver should persist multi-day routines after timezone change") {
            storedRoutineNames() == listOf("Timezone changed restore")
        }
        waitUntil("BootReceiver should reschedule every enabled day after timezone change") {
            findRoutinePendingIntent(TEST_ROUTINE_ID, DayOfWeek.MONDAY) != null &&
                findRoutinePendingIntent(TEST_ROUTINE_ID, DayOfWeek.WEDNESDAY) != null
        }

        assertEquals(listOf("Timezone changed restore"), storedRoutineNames())
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID, DayOfWeek.MONDAY))
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID, DayOfWeek.WEDNESDAY))
    }

    @Test
    fun manifestMarksBootReceiverNotExported() {
        val receiverInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getReceiverInfo(
                ComponentName(context, BootReceiver::class.java),
                PackageManager.ComponentInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getReceiverInfo(
                ComponentName(context, BootReceiver::class.java),
                0,
            )
        }

        assertFalse(receiverInfo.exported)
    }

    @Test
    fun packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm() = runBlocking {
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Package replaced restore"))
        val receiver = BootReceiver().apply {
            routineScheduler = RoutineScheduler(context)
            routineDao = database.routineDao()
            dataStore = this@ReceiverRuntimeIntegrationTest.dataStore
        }

        receiver.restoreRoutinesForBoot(Intent.ACTION_MY_PACKAGE_REPLACED)

        waitUntil("BootReceiver should persist routines into DataStore after package replace") {
            storedRoutineNames() == listOf("Package replaced restore")
        }
        waitUntil("BootReceiver should schedule the restored routine after package replace") {
            findRoutinePendingIntent(TEST_ROUTINE_ID) != null
        }

        assertEquals(listOf("Package replaced restore"), storedRoutineNames())
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
    }

    @Test
    fun routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine() = runBlocking {
        grantPostNotificationsPermission()
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Morning focus"))
        val receiver = RoutineAlarmReceiver().apply {
            notificationHelper = NotificationHelper(context)
            routineScheduler = RoutineScheduler(context)
            routineDao = database.routineDao()
            dataStore = this@ReceiverRuntimeIntegrationTest.dataStore
            appContext = context
        }

        receiver.handleRoutineAlarm(
            action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM,
            routineName = "Morning focus",
            routineId = TEST_ROUTINE_ID,
        )

        waitUntil("RoutineAlarmReceiver should post a notification") {
            activeNotificationIds().contains(TEST_ROUTINE_ID.toInt())
        }
        waitUntil("RoutineAlarmReceiver should rehydrate DataStore routines from Room") {
            storedRoutineNames() == listOf("Morning focus")
        }
        waitUntil("RoutineAlarmReceiver should reschedule enabled routine") {
            findRoutinePendingIntent(TEST_ROUTINE_ID) != null
        }

        assertTrue(activeNotificationIds().contains(TEST_ROUTINE_ID.toInt()))
        assertEquals(listOf("Morning focus"), storedRoutineNames())
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
    }

    @Test
    fun routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine() = runBlocking {
        assertTrue(
            "Disable POST_NOTIFICATION with host adb/appops before running this focused test",
            !NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
        val eveningRoutineId = TEST_ROUTINE_ID + 1
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Morning focus"))
        database.routineDao().insert(enabledRoutineEntity(id = eveningRoutineId, name = "Evening focus"))
        val receiver = RoutineAlarmReceiver().apply {
            notificationHelper = NotificationHelper(context)
            routineScheduler = RoutineScheduler(context)
            routineDao = database.routineDao()
            dataStore = this@ReceiverRuntimeIntegrationTest.dataStore
            appContext = context
        }

        receiver.handleRoutineAlarm(
            action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM,
            routineName = "Morning focus",
            routineId = TEST_ROUTINE_ID,
        )
        receiver.handleRoutineAlarm(
            action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM,
            routineName = "Evening focus",
            routineId = eveningRoutineId,
        )

        val expectedNoticeMessages = listOf(
            context.getString(R.string.routine_notification_permission_fallback_message, "Morning focus"),
            context.getString(R.string.routine_notification_permission_fallback_message, "Evening focus"),
        )
        waitUntil("RoutineAlarmReceiver should queue every fallback notice when notifications are denied") {
            storedRoutineStartNoticeMessages() == expectedNoticeMessages
        }
        waitUntil("RoutineAlarmReceiver should rehydrate DataStore routines from Room when notifications are denied") {
            storedRoutineNames() == listOf("Morning focus", "Evening focus")
        }
        waitUntil("RoutineAlarmReceiver should reschedule enabled routines when notifications are denied") {
            findRoutinePendingIntent(TEST_ROUTINE_ID) != null && findRoutinePendingIntent(eveningRoutineId) != null
        }

        assertEquals(emptySet<Int>(), activeNotificationIds())
        assertEquals(expectedNoticeMessages, storedRoutineStartNoticeMessages())
        assertEquals(listOf("Morning focus", "Evening focus"), storedRoutineNames())
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
        assertNotNull(findRoutinePendingIntent(eveningRoutineId))
    }

    private fun grantPostNotificationsPermission() {
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} POST_NOTIFICATION allow",
        ).close()
        waitUntil("POST_NOTIFICATIONS should be enabled for test setup") {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun grantExactAlarmPermission() {
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow",
        ).close()
    }

    private fun clearAppState() = runBlocking {
        context.deleteDatabase(DATABASE_NAME)
        cancelRoutineAlarm(TEST_ROUTINE_ID)
        cancelRoutineAlarm(TEST_ROUTINE_ID + 1)
        cancelNotification(TEST_ROUTINE_ID)
        cancelNotification(TEST_ROUTINE_ID + 1)
        dataStoreFile().delete()
        dataStoreFile().parentFile?.listFiles()
            ?.filter { it.name.startsWith(DATASTORE_PREFIX) }
            ?.forEach(File::delete)
    }

    private fun storedRoutineNames(): List<String> {
        val preferences = runBlocking {
            runCatching { dataStore.data.first() }.getOrElse { emptyPreferences() }
        }
        val storedJson = preferences[PreferencesKey.ROUTINES] ?: return emptyList()
        return Json.decodeFromString<List<RoutineModel>>(storedJson).map { it.name }
    }

    private fun storedRoutineStartNoticeMessages(): List<String> {
        val preferences = runBlocking {
            runCatching { dataStore.data.first() }.getOrElse { emptyPreferences() }
        }
        return RoutineReceiverPolicy.decodePendingRoutineStartNotices(
            preferences[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE],
        )
    }

    private fun findRoutinePendingIntent(
        routineId: Long,
        dayOfWeek: DayOfWeek = today,
    ): PendingIntent? {
        val requestCode = (routineId * 10 + dayOfWeek.ordinal).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, RoutineAlarmReceiver::class.java).apply {
                action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelRoutineAlarm(routineId: Long) {
        DayOfWeek.entries.forEach { dayOfWeek ->
            findRoutinePendingIntent(routineId, dayOfWeek)?.cancel()
        }
    }

    private fun activeNotificationIds(): Set<Int> {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.map { it.id }.toSet()
    }

    private fun cancelNotification(routineId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(routineId.toInt())
    }

    private fun matchingReceiverClassNames(action: String): Set<String> {
        val intent = Intent(action).setPackage(context.packageName)
        val receivers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)
        }

        return receivers.mapNotNull { it.activityInfo?.name }.toSet()
    }

    private fun enabledRoutineEntity(
        id: Long,
        name: String,
        repeatDays: List<DayOfWeek> = listOf(today),
    ): RoutineEntity {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val startTotalMinutes = (now.time.hour * 60 + now.time.minute + 10) % (24 * 60)
        val endTotalMinutes = (startTotalMinutes + 30) % (24 * 60)
        return RoutineEntity(
            id = id,
            name = name,
            startTime = LocalTime(hour = startTotalMinutes / 60, minute = startTotalMinutes % 60),
            endTime = LocalTime(hour = endTotalMinutes / 60, minute = endTotalMinutes % 60),
            repeatDays = repeatDays,
            lockApplications = listOf("com.example.blocked"),
            isEnabled = true,
            changeLockHours = null,
        )
    }

    private fun createDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { dataStoreFile() },
    )

    private fun dataStoreFile() = context.preferencesDataStoreFile(dataStoreName)

    private fun waitUntil(message: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(100)
        }
        assertTrue(message, condition())
    }

    companion object {
        private const val DATABASE_NAME = "keep-database"
        private const val DATASTORE_PREFIX = "receiver-runtime-integration"
        private const val TEST_ROUTINE_ID = 27L
        private val today: DayOfWeek = java.time.LocalDate.now().dayOfWeek
    }
}

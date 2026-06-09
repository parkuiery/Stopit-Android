package com.uiery.keep.receiver

import com.uiery.keep.data.routine.RoutineRepository
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.database.KeepDatabase
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.data.routine.RoomRoutineRepository
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toModel
import com.uiery.keep.notification.NotificationHelper
import com.uiery.keep.notification.RoutineIdentifierPolicy
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class ReceiverExactAlarmPermissionIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var database: KeepDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreName: String

    @Before
    fun setUp() {
        runBlocking {
            dataStoreName = "$DATASTORE_PREFIX-${System.currentTimeMillis()}-${System.nanoTime()}"
            clearAppState()
            database = Room.databaseBuilder(context, KeepDatabase::class.java, DATABASE_NAME)
                .allowMainThreadQueries()
                .build()
            dataStore = createDataStore()
            setHasShownAlarmPermission(true)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            cancelRoutineAlarms(TEST_ROUTINE_ID)
            cancelNotification(TEST_ROUTINE_ID)
            database.close()
            clearAppState()
        }
    }

    @Test
    fun bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent() = runBlocking {
        assertTrue(
            "Disable SCHEDULE_EXACT_ALARM with host adb/appops before running this focused test",
            !RoutineScheduler(context).canScheduleExactAlarms(),
        )
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Boot deny"))
        val receiver = BootReceiver().apply {
            routineScheduler = RoutineScheduler(context)
            routineRepository = RoomRoutineRepository(database.routineDao())
            dataStore = this@ReceiverExactAlarmPermissionIntegrationTest.dataStore
        }

        receiver.restoreRoutinesForBoot(Intent.ACTION_BOOT_COMPLETED)

        waitUntil("BootReceiver should disable the routine in DataStore when exact alarm permission is missing") {
            storedRoutineEnabledStates() == listOf(false)
        }

        assertEquals(false, database.routineDao().fetch(TEST_ROUTINE_ID).isEnabled)
        assertEquals(listOf(false), storedRoutineEnabledStates())
        assertFalse(hasShownAlarmPermission())
        assertNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
    }

    @Test
    fun bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm() = runBlocking {
        val repeatDays = multiDayRepeatDays()
        assertTrue(
            "Disable SCHEDULE_EXACT_ALARM with host adb/appops before running this focused test",
            !RoutineScheduler(context).canScheduleExactAlarms(),
        )
        val receiver = BootReceiver().apply {
            routineScheduler = RoutineScheduler(context)
            routineRepository = RoomRoutineRepository(database.routineDao())
            dataStore = this@ReceiverExactAlarmPermissionIntegrationTest.dataStore
        }
        database.routineDao().insert(
            enabledRoutineEntity(
                id = TEST_ROUTINE_ID,
                name = "Boot deny multi-day",
                repeatDays = repeatDays,
            ),
        )

        seedRoutinePendingIntents(TEST_ROUTINE_ID, "Boot restore deny multi-day", repeatDays)
        assertRoutinePendingIntentsMatchRepeatDays(TEST_ROUTINE_ID, repeatDays)

        receiver.restoreRoutinesForBoot(Intent.ACTION_BOOT_COMPLETED)

        waitUntil("BootReceiver should disable the multi-day routine in DataStore when exact alarm permission is missing") {
            storedRoutineEnabledStates() == listOf(false)
        }

        assertEquals(false, database.routineDao().fetch(TEST_ROUTINE_ID).isEnabled)
        assertEquals(listOf(false), storedRoutineEnabledStates())
        assertFalse(hasShownAlarmPermission())
        assertNoPendingIntentsForAnyDay(TEST_ROUTINE_ID)
    }

    @Test
    fun packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent() = runBlocking {
        assertTrue(
            "Disable SCHEDULE_EXACT_ALARM with host adb/appops before running this focused test",
            !RoutineScheduler(context).canScheduleExactAlarms(),
        )
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Package replaced deny"))
        val receiver = BootReceiver().apply {
            routineScheduler = RoutineScheduler(context)
            routineRepository = RoomRoutineRepository(database.routineDao())
            dataStore = this@ReceiverExactAlarmPermissionIntegrationTest.dataStore
        }

        receiver.restoreRoutinesForBoot(Intent.ACTION_MY_PACKAGE_REPLACED)

        waitUntil("Package replaced restore should disable the routine in DataStore when exact alarm permission is missing") {
            storedRoutineEnabledStates() == listOf(false)
        }

        assertEquals(false, database.routineDao().fetch(TEST_ROUTINE_ID).isEnabled)
        assertEquals(listOf(false), storedRoutineEnabledStates())
        assertFalse(hasShownAlarmPermission())
        assertNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
    }

    @Test
    fun packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm() = runBlocking {
        val repeatDays = multiDayRepeatDays()
        assertTrue(
            "Disable SCHEDULE_EXACT_ALARM with host adb/appops before running this focused test",
            !RoutineScheduler(context).canScheduleExactAlarms(),
        )
        val receiver = BootReceiver().apply {
            routineScheduler = RoutineScheduler(context)
            routineRepository = RoomRoutineRepository(database.routineDao())
            dataStore = this@ReceiverExactAlarmPermissionIntegrationTest.dataStore
        }
        database.routineDao().insert(
            enabledRoutineEntity(
                id = TEST_ROUTINE_ID,
                name = "Package replaced deny multi-day",
                repeatDays = repeatDays,
            ),
        )

        seedRoutinePendingIntents(TEST_ROUTINE_ID, "Package replaced deny multi-day", repeatDays)
        assertRoutinePendingIntentsMatchRepeatDays(TEST_ROUTINE_ID, repeatDays)

        receiver.restoreRoutinesForBoot(Intent.ACTION_MY_PACKAGE_REPLACED)

        waitUntil("Package replaced restore should disable the multi-day routine in DataStore when exact alarm permission is missing") {
            storedRoutineEnabledStates() == listOf(false)
        }

        assertEquals(false, database.routineDao().fetch(TEST_ROUTINE_ID).isEnabled)
        assertEquals(listOf(false), storedRoutineEnabledStates())
        assertFalse(hasShownAlarmPermission())
        assertNoPendingIntentsForAnyDay(TEST_ROUTINE_ID)
    }

    @Test
    fun routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent() = runBlocking {
        assertTrue(
            "Disable SCHEDULE_EXACT_ALARM with host adb/appops before running this focused test",
            !RoutineScheduler(context).canScheduleExactAlarms(),
        )
        grantPostNotificationsPermission()
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Morning focus"))
        val receiver = RoutineAlarmReceiver().apply {
            notificationHelper = NotificationHelper(context)
            routineScheduler = RoutineScheduler(context)
            routineRepository = RoomRoutineRepository(database.routineDao())
            dataStore = this@ReceiverExactAlarmPermissionIntegrationTest.dataStore
            appContext = context
        }

        receiver.handleRoutineAlarm(
            action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM,
            routineName = "Morning focus",
            routineId = TEST_ROUTINE_ID,
        )

        waitUntil("RoutineAlarmReceiver should post the current routine-start notification even when exact alarm permission is missing") {
            activeNotificationIds().contains(RoutineIdentifierPolicy.routineStartNotificationId(TEST_ROUTINE_ID))
        }
        waitUntil("RoutineAlarmReceiver should disable the routine in DataStore when exact alarm permission is missing") {
            storedRoutineEnabledStates() == listOf(false)
        }

        assertTrue(activeNotificationIds().contains(RoutineIdentifierPolicy.routineStartNotificationId(TEST_ROUTINE_ID)))
        assertEquals(false, database.routineDao().fetch(TEST_ROUTINE_ID).isEnabled)
        assertEquals(listOf(false), storedRoutineEnabledStates())
        assertFalse(hasShownAlarmPermission())
        assertNotNull(findPostedNotification(TEST_ROUTINE_ID))
        assertNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
    }

    @Test
    fun routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm() = runBlocking {
        grantPostNotificationsPermission()
        val repeatDays = multiDayRepeatDays()
        assertTrue(
            "Disable SCHEDULE_EXACT_ALARM with host adb/appops before running this focused test",
            !RoutineScheduler(context).canScheduleExactAlarms(),
        )
        database.routineDao().insert(
            enabledRoutineEntity(
                id = TEST_ROUTINE_ID,
                name = "Morning focus multi-day",
                repeatDays = repeatDays,
            ),
        )
        val receiver = RoutineAlarmReceiver().apply {
            notificationHelper = NotificationHelper(context)
            routineScheduler = RoutineScheduler(context)
            routineRepository = RoomRoutineRepository(database.routineDao())
            dataStore = this@ReceiverExactAlarmPermissionIntegrationTest.dataStore
            appContext = context
        }

        seedRoutinePendingIntents(TEST_ROUTINE_ID, "Morning focus multi-day", repeatDays)
        assertRoutinePendingIntentsMatchRepeatDays(TEST_ROUTINE_ID, repeatDays)

        receiver.handleRoutineAlarm(
            action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM,
            routineName = "Morning focus multi-day",
            routineId = TEST_ROUTINE_ID,
        )

        waitUntil("RoutineAlarmReceiver should disable the multi-day routine in DataStore when exact alarm permission is missing") {
            storedRoutineEnabledStates() == listOf(false)
        }

        assertTrue(activeNotificationIds().contains(RoutineIdentifierPolicy.routineStartNotificationId(TEST_ROUTINE_ID)))
        assertEquals(false, database.routineDao().fetch(TEST_ROUTINE_ID).isEnabled)
        assertEquals(listOf(false), storedRoutineEnabledStates())
        assertFalse(hasShownAlarmPermission())
        assertNotNull(findPostedNotification(TEST_ROUTINE_ID))
        assertNoPendingIntentsForAnyDay(TEST_ROUTINE_ID)
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

    private suspend fun setHasShownAlarmPermission(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.HAS_SHOWN_ALARM_PERMISSION] = value
        }
    }

    private fun hasShownAlarmPermission(): Boolean {
        val preferences = runBlocking {
            runCatching { dataStore.data.first() }.getOrElse { emptyPreferences() }
        }
        return preferences[PreferencesKey.HAS_SHOWN_ALARM_PERMISSION] ?: false
    }

    private fun setExactAlarmPermissionAllowed() {
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow",
        ).close()
        waitUntil("SCHEDULE_EXACT_ALARM allow should be visible before exact alarm allow-path assertions") {
            RoutineScheduler(context).canScheduleExactAlarms()
        }
    }

    private fun setExactAlarmPermissionDenied() {
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SCHEDULE_EXACT_ALARM deny",
        ).close()
        waitUntil("SCHEDULE_EXACT_ALARM deny should be visible before exact alarm deny-path assertions") {
            !RoutineScheduler(context).canScheduleExactAlarms()
        }
    }

    private fun clearAppState() = runBlocking {
        context.deleteDatabase(DATABASE_NAME)
        cancelRoutineAlarms(TEST_ROUTINE_ID)
        cancelNotification(TEST_ROUTINE_ID)
        dataStoreFile().delete()
        dataStoreFile().parentFile?.listFiles()
            ?.filter { it.name.startsWith(DATASTORE_PREFIX) }
            ?.forEach(File::delete)
    }

    private fun storedRoutineEnabledStates(): List<Boolean> {
        val preferences = runBlocking {
            runCatching { dataStore.data.first() }.getOrElse { emptyPreferences() }
        }
        val storedJson = preferences[PreferencesKey.ROUTINES] ?: return emptyList()
        return Json.decodeFromString<List<RoutineModel>>(storedJson).map { it.isEnabled }
    }

    private fun findRoutinePendingIntent(routineId: Long): PendingIntent? =
        findRoutinePendingIntent(routineId, today)

    private fun findRoutinePendingIntent(routineId: Long, dayOfWeek: DayOfWeek): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            RoutineIdentifierPolicy.alarmRequestCode(routineId, dayOfWeek),
            Intent(context, RoutineAlarmReceiver::class.java).apply {
                action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM
                data = RoutineIdentifierPolicy.alarmIntentData(routineId, dayOfWeek)
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelRoutineAlarms(routineId: Long, days: Iterable<DayOfWeek> = DayOfWeek.entries) {
        days.forEach { dayOfWeek ->
            findRoutinePendingIntent(routineId, dayOfWeek)?.cancel()
        }
    }

    private fun seedRoutinePendingIntents(routineId: Long, routineName: String, repeatDays: Iterable<DayOfWeek>) {
        repeatDays.forEach { dayOfWeek ->
            PendingIntent.getBroadcast(
                context,
                RoutineIdentifierPolicy.alarmRequestCode(routineId, dayOfWeek),
                Intent(context, RoutineAlarmReceiver::class.java).apply {
                    action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM
                    data = RoutineIdentifierPolicy.alarmIntentData(routineId, dayOfWeek)
                    putExtra(RoutineAlarmReceiver.EXTRA_ROUTINE_NAME, routineName)
                    putExtra(RoutineAlarmReceiver.EXTRA_ROUTINE_ID, routineId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    private fun assertRoutinePendingIntentsMatchRepeatDays(routineId: Long, repeatDays: List<DayOfWeek>) {
        val missingDays = repeatDays.filter { dayOfWeek -> findRoutinePendingIntent(routineId, dayOfWeek) == null }
        val unexpectedDays = DayOfWeek.entries
            .filterNot(repeatDays::contains)
            .filter { dayOfWeek -> findRoutinePendingIntent(routineId, dayOfWeek) != null }

        assertEquals(emptyList<DayOfWeek>(), missingDays)
        assertEquals(emptyList<DayOfWeek>(), unexpectedDays)
    }

    private fun assertNoPendingIntentsForAnyDay(routineId: Long) {
        val remainingDays = DayOfWeek.entries.filter { dayOfWeek -> findRoutinePendingIntent(routineId, dayOfWeek) != null }
        assertEquals(emptyList<DayOfWeek>(), remainingDays)
    }

    private fun activeNotificationIds(): Set<Int> {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.map { it.id }.toSet()
    }

    private fun findPostedNotification(routineId: Long) =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .activeNotifications
            .firstOrNull { it.id == RoutineIdentifierPolicy.routineStartNotificationId(routineId) }

    private fun cancelNotification(routineId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(RoutineIdentifierPolicy.routineStartNotificationId(routineId))
        manager.cancel(routineId.toInt())
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

    private fun multiDayRepeatDays(): List<DayOfWeek> = listOf(today, today.plus(2), today.plus(4)).distinct()

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
        private const val DATASTORE_PREFIX = "receiver-exact-alarm-integration"
        private const val TEST_ROUTINE_ID = 137L
        private val today: DayOfWeek = java.time.LocalDate.now().dayOfWeek
    }
}

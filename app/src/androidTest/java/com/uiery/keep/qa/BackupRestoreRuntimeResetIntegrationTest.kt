package com.uiery.keep.qa

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.RoutineCountAnalyticsSync
import com.uiery.keep.database.KeepDatabase
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.BackupRestoreDataStoreKeyPolicy
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.feature.routine.RoomRoutineRepository
import com.uiery.keep.feature.routine.RoutineExactAlarmOrchestrator
import com.uiery.keep.feature.routine.RoutineRestoreAftercare
import com.uiery.keep.feature.routine.RoutineViewModel
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.NotificationHelper
import com.uiery.keep.notification.RoutineIdentifierPolicy
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.receiver.BootReceiver
import com.uiery.keep.receiver.RoutineAlarmReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek

/**
 * Emulates the intended backup/restore contract for a transferred device:
 * - Room DB content is restored
 * - keep-datastore.preferences_pb is excluded, so runtime-only DataStore keys start empty
 * - Boot/routine entry points must rehydrate PreferencesKey.ROUTINES without reviving other runtime state
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreRuntimeResetIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var database: KeepDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreName: String

    @Before
    fun setUp() {
        runBlocking {
            dataStoreName = "$DATASTORE_PREFIX-${System.currentTimeMillis()}-${System.nanoTime()}"
            grantPostNotificationsPermission()
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
    fun bootRestoreRehydratesRoomRoutineCacheWithoutRevivingResetOnlyDataStoreState() = runBlocking {
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Restore boot routine"))
        val receiver = BootReceiver().apply {
            routineScheduler = RoutineScheduler(context)
            routineRepository = RoomRoutineRepository(database.routineDao())
            dataStore = this@BackupRestoreRuntimeResetIntegrationTest.dataStore
        }

        receiver.restoreRoutinesForBoot(Intent.ACTION_BOOT_COMPLETED)

        waitUntil("BootReceiver should persist restored routines into DataStore") {
            storedRoutineNames() == listOf("Restore boot routine")
        }
        waitUntil("BootReceiver should schedule restored routine after rehydration") {
            findRoutinePendingIntent(TEST_ROUTINE_ID) != null
        }

        assertEquals(listOf("Restore boot routine"), storedRoutineNames())
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
        assertRestoreResetOnlyStateAbsent()
    }

    @Test
    fun routineAlarmRestoreRehydratesRoomRoutineCacheWithoutRevivingResetOnlyDataStoreState() = runBlocking {
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Restore alarm routine"))
        val receiver = RoutineAlarmReceiver().apply {
            notificationHelper = NotificationHelper(context)
            routineScheduler = RoutineScheduler(context)
            routineRepository = RoomRoutineRepository(database.routineDao())
            dataStore = this@BackupRestoreRuntimeResetIntegrationTest.dataStore
            appContext = context
        }

        receiver.handleRoutineAlarm(
            action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM,
            routineName = "Restore alarm routine",
            routineId = TEST_ROUTINE_ID,
        )

        waitUntil("RoutineAlarmReceiver should post routine notification") {
            activeNotificationIds().contains(RoutineIdentifierPolicy.routineStartNotificationId(TEST_ROUTINE_ID))
        }
        waitUntil("RoutineAlarmReceiver should persist restored routines into DataStore") {
            storedRoutineNames() == listOf("Restore alarm routine")
        }
        waitUntil("RoutineAlarmReceiver should reschedule restored routine") {
            findRoutinePendingIntent(TEST_ROUTINE_ID) != null
        }

        assertTrue(activeNotificationIds().contains(RoutineIdentifierPolicy.routineStartNotificationId(TEST_ROUTINE_ID)))
        assertEquals(listOf("Restore alarm routine"), storedRoutineNames())
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
        assertRestoreResetOnlyStateAbsent()
    }

    @Test
    fun appOpenRoutineEntryRehydratesRoomRoutineCacheAndSchedulesAlarmWithoutRevivingResetOnlyDataStoreState() = runBlocking {
        database.routineDao().insert(enabledRoutineEntity(id = TEST_ROUTINE_ID, name = "Restore app-open routine"))
        val scheduler = RoutineScheduler(context)
        val noticeStore = RoutineNoticeStore(dataStore)
        val routineRepository = RoomRoutineRepository(database.routineDao())

        RoutineViewModel(
            routineRepository = routineRepository,
            dataStore = dataStore,
            analytics = NoopBackupRestoreAnalytics,
            routineCountAnalyticsSync = RoutineCountAnalyticsSync(database.routineDao(), NoopBackupRestoreAnalytics),
            exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(scheduler),
            routineNoticeStore = noticeStore,
            routineRestoreAftercare = RoutineRestoreAftercare(
                routineRepository = routineRepository,
                dataStore = dataStore,
                exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(scheduler),
                routineNoticeStore = noticeStore,
            ),
        )

        waitUntil("Routine screen app-open path should persist restored routines into DataStore") {
            storedRoutineNames() == listOf("Restore app-open routine")
        }
        waitUntil("Routine screen app-open path should schedule restored routine PendingIntent") {
            findRoutinePendingIntent(TEST_ROUTINE_ID) != null
        }

        assertEquals(listOf("Restore app-open routine"), storedRoutineNames())
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
        assertRestoreResetOnlyStateAbsent()
    }

    private fun grantPostNotificationsPermission() {
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
    }

    private fun grantExactAlarmPermission() {
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow",
        ).close()
    }

    private fun assertRestoreResetOnlyStateAbsent() {
        val preferences = runBlocking {
            runCatching { dataStore.data.first() }.getOrElse { emptyPreferences() }
        }

        BackupRestoreDataStoreKeyPolicy.resetOnlyKeys.forEach { key ->
            assertNull("${key.name} should remain absent after restored-device runtime rehydration", preferences[key])
        }
    }

    private fun clearAppState() = runBlocking {
        context.deleteDatabase(DATABASE_NAME)
        cancelRoutineAlarm(TEST_ROUTINE_ID)
        cancelNotification(TEST_ROUTINE_ID)
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

    private fun findRoutinePendingIntent(routineId: Long): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            RoutineIdentifierPolicy.alarmRequestCode(routineId, today),
            Intent(context, RoutineAlarmReceiver::class.java).apply {
                action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM
                data = RoutineIdentifierPolicy.alarmIntentData(routineId, today)
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelRoutineAlarm(routineId: Long) {
        findRoutinePendingIntent(routineId)?.cancel()
        PendingIntent.getBroadcast(
            context,
            RoutineIdentifierPolicy.legacyAlarmRequestCode(routineId, today),
            Intent(context, RoutineAlarmReceiver::class.java).apply {
                action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.cancel()
    }

    private fun activeNotificationIds(): Set<Int> {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.map { it.id }.toSet()
    }

    private fun cancelNotification(routineId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(RoutineIdentifierPolicy.routineStartNotificationId(routineId))
        manager.cancel(routineId.toInt())
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
        private const val DATASTORE_PREFIX = "backup-restore-runtime-reset"
        private const val TEST_ROUTINE_ID = 103L
        private val today: DayOfWeek = java.time.LocalDate.now().dayOfWeek
    }
}

private object NoopBackupRestoreAnalytics : KeepAnalytics {
    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}

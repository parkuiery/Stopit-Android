package com.uiery.keep.feature.routine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.KeepDatabase
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.receiver.RoutineAlarmReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
class RoutineExactAlarmPermissionIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var database: KeepDatabase
    private lateinit var dataStoreName: String

    @Before
    fun setUp() {
        runBlocking {
            dataStoreName = "$DATASTORE_PREFIX-${System.currentTimeMillis()}-${System.nanoTime()}"
            clearAppState()
            database = Room.databaseBuilder(context, KeepDatabase::class.java, DATABASE_NAME)
                .allowMainThreadQueries()
                .build()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            cancelRoutineAlarm(TEST_ROUTINE_ID)
            database.close()
            clearAppState()
        }
    }

    @Test
    fun addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt() = runBlocking {
        assertFalse(RoutineScheduler(context).canScheduleExactAlarms())
        val analytics = RecordingKeepAnalytics()
        val viewModel = RoutineBottomSheetViewModel(
            routineDao = database.routineDao(),
            routineScheduler = RoutineScheduler(context),
            analytics = analytics,
        )

        viewModel.resetState()
        viewModel.setName("Morning focus")
        viewModel.setStartTime(nextStartTime())
        viewModel.setEndTime(nextEndTime())
        viewModel.setSelectDays(today)
        viewModel.setSelectApps(setOf("com.example.blocked"))
        viewModel.addRoutine()

        val sideEffect = withTimeout(5_000) { viewModel.container.sideEffectFlow.first() }
        waitUntil("Routine should be stored") { database.routineDao().fetchAllOnce().isNotEmpty() }
        val savedRoutine = database.routineDao().fetchAllOnce().single()

        assertEquals(RoutineBottomSheetSideEffect.ShowAlarmPermission, sideEffect)
        assertFalse(savedRoutine.isEnabled)
        assertEquals(0, analytics.lockScheduledCalls)
        assertNoScheduledAlarm(TEST_ROUTINE_ID)
    }

    @Test
    fun addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt() = runBlocking {
        val repeatDays = multiDayRepeatDays()
        assertFalse(RoutineScheduler(context).canScheduleExactAlarms())
        val analytics = RecordingKeepAnalytics()
        val viewModel = RoutineBottomSheetViewModel(
            routineDao = database.routineDao(),
            routineScheduler = RoutineScheduler(context),
            analytics = analytics,
        )

        viewModel.resetState()
        viewModel.setName("Morning focus multi-day")
        viewModel.setStartTime(nextStartTime())
        viewModel.setEndTime(nextEndTime())
        repeatDays.forEach { dayOfWeek -> viewModel.setSelectDays(dayOfWeek) }
        viewModel.setSelectApps(setOf("com.example.blocked"))
        viewModel.addRoutine()

        val sideEffect = withTimeout(5_000) { viewModel.container.sideEffectFlow.first() }
        waitUntil("Multi-day routine should be stored") { database.routineDao().fetchAllOnce().isNotEmpty() }
        val savedRoutine = database.routineDao().fetchAllOnce().single()

        assertEquals(RoutineBottomSheetSideEffect.ShowAlarmPermission, sideEffect)
        assertFalse(savedRoutine.isEnabled)
        assertEquals(repeatDays.toSet(), savedRoutine.repeatDays.toSet())
        assertEquals(0, analytics.lockScheduledCalls)
        assertNoScheduledAlarms(TEST_ROUTINE_ID)
    }

    @Test
    fun enablingRoutineWithExactAlarmPermissionSchedulesAlarm() = runBlocking {
        assertTrue(RoutineScheduler(context).canScheduleExactAlarms())
        database.routineDao().insert(disabledRoutineEntity(TEST_ROUTINE_ID, "Grant path"))
        val viewModel = RoutineViewModel(
            routineDao = database.routineDao(),
            dataStore = createDataStore(),
            analytics = RecordingKeepAnalytics(),
            routineScheduler = RoutineScheduler(context),
        )

        waitUntil("Routine list should load from Room") {
            viewModel.container.stateFlow.value.routines.any { it.id == TEST_ROUTINE_ID }
        }

        viewModel.changeEnabled(TEST_ROUTINE_ID, true)

        waitUntil("Exact-alarm grant path should schedule the routine") {
            findRoutinePendingIntent(TEST_ROUTINE_ID) != null
        }

        assertTrue(database.routineDao().fetch(TEST_ROUTINE_ID).isEnabled)
        assertNotNull(findRoutinePendingIntent(TEST_ROUTINE_ID))
    }

    @Test
    fun enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm() = runBlocking {
        val repeatDays = multiDayRepeatDays()
        assertTrue(RoutineScheduler(context).canScheduleExactAlarms())
        database.routineDao().insert(
            disabledRoutineEntity(
                id = TEST_ROUTINE_ID,
                name = "Grant path multi-day",
                repeatDays = repeatDays,
            ),
        )
        val viewModel = RoutineViewModel(
            routineDao = database.routineDao(),
            dataStore = createDataStore(),
            analytics = RecordingKeepAnalytics(),
            routineScheduler = RoutineScheduler(context),
        )

        waitUntil("Multi-day routine list should load from Room") {
            viewModel.container.stateFlow.value.routines.any { it.id == TEST_ROUTINE_ID }
        }

        viewModel.changeEnabled(TEST_ROUTINE_ID, true)

        waitUntil("Exact-alarm grant path should schedule every repeat day alarm") {
            repeatDays.all { dayOfWeek -> findRoutinePendingIntent(TEST_ROUTINE_ID, dayOfWeek) != null }
        }

        assertTrue(database.routineDao().fetch(TEST_ROUTINE_ID).isEnabled)
        assertRoutinePendingIntentsMatchRepeatDays(TEST_ROUTINE_ID, repeatDays)
    }

    @Test
    fun cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent() = runBlocking {
        val repeatDays = multiDayRepeatDays()
        assertTrue(RoutineScheduler(context).canScheduleExactAlarms())
        database.routineDao().insert(
            disabledRoutineEntity(
                id = TEST_ROUTINE_ID,
                name = "Cleanup multi-day",
                repeatDays = repeatDays,
            ),
        )
        val viewModel = RoutineViewModel(
            routineDao = database.routineDao(),
            dataStore = createDataStore(),
            analytics = RecordingKeepAnalytics(),
            routineScheduler = RoutineScheduler(context),
        )

        waitUntil("Cleanup multi-day routine list should load from Room") {
            viewModel.container.stateFlow.value.routines.any { it.id == TEST_ROUTINE_ID }
        }

        viewModel.changeEnabled(TEST_ROUTINE_ID, true)

        waitUntil("Cleanup test should schedule every repeat day alarm") {
            repeatDays.all { dayOfWeek -> findRoutinePendingIntent(TEST_ROUTINE_ID, dayOfWeek) != null }
        }

        cancelRoutineAlarm(TEST_ROUTINE_ID)

        assertNoScheduledAlarms(TEST_ROUTINE_ID)
    }

    private fun nextStartTime(): LocalTime {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val startTotalMinutes = (now.time.hour * 60 + now.time.minute + 10) % (24 * 60)
        return LocalTime(hour = startTotalMinutes / 60, minute = startTotalMinutes % 60)
    }

    private fun nextEndTime(): LocalTime {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val startTotalMinutes = (now.time.hour * 60 + now.time.minute + 10) % (24 * 60)
        val endTotalMinutes = (startTotalMinutes + 30) % (24 * 60)
        return LocalTime(hour = endTotalMinutes / 60, minute = endTotalMinutes % 60)
    }

    private fun disabledRoutineEntity(
        id: Long,
        name: String,
        repeatDays: List<DayOfWeek> = listOf(today),
    ): RoutineEntity {
        return RoutineEntity(
            id = id,
            name = name,
            startTime = nextStartTime(),
            endTime = nextEndTime(),
            repeatDays = repeatDays,
            lockApplications = listOf("com.example.blocked"),
            isEnabled = false,
            changeLockHours = null,
        )
    }

    private fun findRoutinePendingIntent(routineId: Long): PendingIntent? =
        findRoutinePendingIntent(routineId, today)

    private fun findRoutinePendingIntent(routineId: Long, dayOfWeek: DayOfWeek): PendingIntent? {
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

    private fun assertNoScheduledAlarm(routineId: Long) {
        assertEquals(null, findRoutinePendingIntent(routineId))
    }

    private fun assertNoScheduledAlarms(routineId: Long) {
        val remainingDays = DayOfWeek.entries.filter { dayOfWeek -> findRoutinePendingIntent(routineId, dayOfWeek) != null }
        assertEquals(emptyList<DayOfWeek>(), remainingDays)
    }

    private fun assertRoutinePendingIntentsMatchRepeatDays(routineId: Long, repeatDays: List<DayOfWeek>) {
        val missingDays = repeatDays.filter { dayOfWeek -> findRoutinePendingIntent(routineId, dayOfWeek) == null }
        val unexpectedDays = DayOfWeek.entries
            .filterNot(repeatDays::contains)
            .filter { dayOfWeek -> findRoutinePendingIntent(routineId, dayOfWeek) != null }

        assertEquals(emptyList<DayOfWeek>(), missingDays)
        assertEquals(emptyList<DayOfWeek>(), unexpectedDays)
    }

    private fun cancelRoutineAlarm(routineId: Long) {
        DayOfWeek.entries.forEach { dayOfWeek ->
            findRoutinePendingIntent(routineId, dayOfWeek)?.cancel()
        }
    }

    private fun clearAppState() {
        context.deleteDatabase(DATABASE_NAME)
        cancelRoutineAlarm(TEST_ROUTINE_ID)
        dataStoreFile().delete()
        dataStoreFile().parentFile?.listFiles()
            ?.filter { it.name.startsWith(DATASTORE_PREFIX) }
            ?.forEach(File::delete)
    }

    private fun createDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { dataStoreFile() },
    )

    private fun dataStoreFile() = context.preferencesDataStoreFile(dataStoreName)

    private fun multiDayRepeatDays(): List<DayOfWeek> = listOf(today, today.plus(2), today.plus(4)).distinct()

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

    private class RecordingKeepAnalytics : KeepAnalytics {
        var lockScheduledCalls: Int = 0

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
        override fun trackLockScheduled(scheduleType: String, scheduledDurationMinutes: Long) {
            lockScheduledCalls += 1
        }
    }

    companion object {
        private const val DATABASE_NAME = "keep-database"
        private const val DATASTORE_PREFIX = "routine-exact-alarm"
        private const val TEST_ROUTINE_ID = 77L
        private val today: DayOfWeek = java.time.LocalDate.now().dayOfWeek
    }
}

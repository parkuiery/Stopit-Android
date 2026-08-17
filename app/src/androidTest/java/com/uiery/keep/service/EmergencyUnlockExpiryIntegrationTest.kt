package com.uiery.keep.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.analytics.AnalyticsBackend
import com.uiery.keep.analytics.EmergencyUnlockCompletionCoordinator
import com.uiery.keep.analytics.FirebaseKeepAnalytics
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.uiery.keep.testing.AndroidTestConditionWaiter

@RunWith(AndroidJUnit4::class)
class EmergencyUnlockExpiryIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = runBlocking {
        clearEmergencyUnlockState()
        cancelEmergencyUnlockNotification(context)
    }

    @After
    fun tearDown() = runBlocking {
        clearEmergencyUnlockState()
        cancelEmergencyUnlockNotification(context)
    }

    @Test
    fun emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification() {
        assumeFalse(
            "Disable POST_NOTIFICATION with host adb/appops before running this focused test",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )

        val helper = EmergencyUnlockNotificationHelper(context)

        assertEquals(
            EmergencyUnlockNotificationPostResult.PermissionDenied,
            helper.showCountdown(remainingSeconds = 60, totalSeconds = 60),
        )
        assertFalse(activeNotificationIds().contains(EmergencyUnlockNotificationHelper.NOTIFICATION_ID))
    }

    /**
     * 실제 teardown 배선을 그대로 지나가는 경로를 검증한다.
     *
     * 기존 만료 테스트는 자기 lambda 를 넘겨서 서비스가 실제로 실행하는 코드와 다른 길을
     * 지나갔다. 그래서 completion 배달 여부는 증명되지 않았다. 여기서는 서비스가 쓰는
     * finishEmergencyUnlockWindow 를 직접 호출한다. (#1167)
     */
    @Test
    fun finishEmergencyUnlockWindow_clearsRuntimeStateAndDeliversCompletionOnce() = runBlocking {
        val store = BlockingStateStore(context.dataStore)
        val backend = RecordingAnalyticsBackend()
        val coordinator = EmergencyUnlockCompletionCoordinator(
            blockingStateStore = store,
            analytics = FirebaseKeepAnalytics(backend),
        )

        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS] = setOf("com.example.blocked")
            preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] = 1_000L
        }
        store.reserveEmergencyUnlockCompletion(
            reason = "work",
            durationMinutes = 5,
            remainingUnlocks = 2,
        )

        finishEmergencyUnlockWindow(blockingStateStore = store, completionCoordinator = coordinator)

        assertEquals(emptySet<String>(), storedUnlockedApps())
        assertNull(storedExpireTimeMillis())
        assertEquals(listOf("emergency_unlock_completed"), backend.loggedEventNames)
        assertEquals(5, backend.loggedParams.single()["duration_minutes"])
        assertEquals(2, backend.loggedParams.single()["remaining_unlocks"])
        assertEquals("work", backend.loggedParams.single()["reason"])
        assertNull(store.readPendingEmergencyUnlockCompletion())

        // 두 만료 경로가 겹쳐 돌아도 완료는 한 번만 기록되어야 한다.
        finishEmergencyUnlockWindow(blockingStateStore = store, completionCoordinator = coordinator)
        assertEquals(1, backend.loggedEventNames.size)
    }

    /**
     * 창이 열린 채 프로세스가 죽었다가 되살아난 경우. 예약은 DataStore 에 남아 있으므로
     * 다음 teardown 에서 배달되어야 한다.
     */
    @Test
    fun finishEmergencyUnlockWindow_deliversReservationThatSurvivedProcessRestart() = runBlocking {
        val store = BlockingStateStore(context.dataStore)
        store.reserveEmergencyUnlockCompletion(
            reason = "rest",
            durationMinutes = 15,
            remainingUnlocks = 0,
        )

        // 재시작 후처럼 새 인스턴스로만 접근한다.
        val restartedStore = BlockingStateStore(context.dataStore)
        val backend = RecordingAnalyticsBackend()
        finishEmergencyUnlockWindow(
            blockingStateStore = restartedStore,
            completionCoordinator = EmergencyUnlockCompletionCoordinator(
                blockingStateStore = restartedStore,
                analytics = FirebaseKeepAnalytics(backend),
            ),
        )

        assertEquals(listOf("emergency_unlock_completed"), backend.loggedEventNames)
        assertEquals("rest", backend.loggedParams.single()["reason"])
        assertNull(restartedStore.readPendingEmergencyUnlockCompletion())
    }

    /** 예약이 없으면 아무것도 보내지 않는다. 두 경로가 서로를 몰라도 안전한 근거다. */
    @Test
    fun finishEmergencyUnlockWindow_withoutReservationSendsNothing() = runBlocking {
        val store = BlockingStateStore(context.dataStore)
        val backend = RecordingAnalyticsBackend()

        finishEmergencyUnlockWindow(
            blockingStateStore = store,
            completionCoordinator = EmergencyUnlockCompletionCoordinator(
                blockingStateStore = store,
                analytics = FirebaseKeepAnalytics(backend),
            ),
        )

        assertEquals(emptyList<String>(), backend.loggedEventNames)
    }

    @Test
    fun handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage() = runBlocking {
        grantPostNotificationsPermission()
        val blockedPackage = "com.example.blocked"
        val expireTimeMillis = 1_000L
        val helper = EmergencyUnlockNotificationHelper(context)
        assertEquals(
            EmergencyUnlockNotificationPostResult.Posted,
            helper.showCountdown(remainingSeconds = 60, totalSeconds = 60),
        )
        waitUntil("Emergency unlock notification should become active after showCountdown") {
            activeNotificationIds().contains(EmergencyUnlockNotificationHelper.NOTIFICATION_ID)
        }

        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS] = setOf(blockedPackage)
            preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] = expireTimeMillis
        }
        EmergencyUnlockState.current = EmergencyUnlockData(
            unlockedApps = setOf(blockedPackage),
            expireTimeMillis = expireTimeMillis,
        )

        val resolution = handleExpiredEmergencyUnlockForContext(
            context = context,
            expectedExpireTimeMillis = expireTimeMillis,
            currentExpireTimeMillis = expireTimeMillis,
            expiredUnlockedApps = setOf(blockedPackage),
            foregroundPackage = blockedPackage,
            applicationId = context.packageName,
            isForegroundStillEmergencyUnlocked = false,
            clearExpiredEmergencyUnlockState = BlockingStateStore(context.dataStore)::clearEmergencyUnlockRuntimeState,
            nowMillis = expireTimeMillis,
        )

        assertEquals(
            EmergencyUnlockExpiryResolution(
                shouldClearState = true,
                packageToReblock = blockedPackage,
            ),
            resolution,
        )
        assertEquals(emptySet<String>(), storedUnlockedApps())
        assertNull(storedExpireTimeMillis())
        assertEquals(EmergencyUnlockData.EMPTY, EmergencyUnlockState.current)
        waitUntil("Emergency unlock notification should be cancelled after expiry handling") {
            !activeNotificationIds().contains(EmergencyUnlockNotificationHelper.NOTIFICATION_ID)
        }
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

    private suspend fun clearEmergencyUnlockState() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
    }

    private suspend fun storedUnlockedApps(): Set<String> =
        context.dataStore.data.first()[PreferencesKey.EMERGENCY_UNLOCK_APPS] ?: emptySet()

    private suspend fun storedExpireTimeMillis(): Long? =
        context.dataStore.data.first()[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME]

    private fun activeNotificationIds(): Set<Int> {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.map { it.id }.toSet()
    }

    private fun waitUntil(message: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            AndroidTestConditionWaiter.pause(50, reason = "polling instrumentation condition")
        }
        throw AssertionError(message)
    }
}

private class RecordingAnalyticsBackend : AnalyticsBackend {
    val loggedEventNames = mutableListOf<String>()
    val loggedParams = mutableListOf<Map<String, Any?>>()

    override fun logEvent(name: String, params: Map<String, Any?>) {
        loggedEventNames += name
        loggedParams += params
    }

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(name: String, value: String) = Unit
}

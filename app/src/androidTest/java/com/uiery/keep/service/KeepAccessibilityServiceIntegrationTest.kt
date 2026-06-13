package com.uiery.keep.service

import android.app.UiAutomation
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.data.goallock.GoalLockRepository
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.dataStore
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.toRepeatDaysBinary
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime as KotlinLocalTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class KeepAccessibilityServiceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device by lazy { UiDevice.getInstance(instrumentation) }

    private var accessibilityServiceInitiallyEnabled = false
    private var originalUiAutomationFlags = 0

    @Before
    fun setUp() {
        runBlocking {
            originalUiAutomationFlags = Configurator.getInstance().uiAutomationFlags
            Configurator.getInstance().setUiAutomationFlags(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
            )
            clearAccessibilityBlockState()
            primeAppProcess()
            device.pressHome()
            accessibilityServiceInitiallyEnabled = isAccessibilityServiceEnabled()
            resetDebugStateRetainingConnectionFlag()
            enableAccessibilityServiceIfNeeded()
            waitUntil("KeepAccessibilityService should be enabled for the runtime test") {
                isAccessibilityServiceEnabled()
            }
            primeAppProcess()
            waitForServiceStatePropagation()
            device.pressHome()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            try {
                clearAccessibilityBlockState()
                restoreAccessibilityServiceState(initiallyEnabled = accessibilityServiceInitiallyEnabled)
                device.pressHome()
            } finally {
                Configurator.getInstance().setUiAutomationFlags(originalUiAutomationFlags)
            }
        }
    }

    @Test
    fun launchCandidateSelection_fallsBackToNextObservedPackageAndRetainsDiagnostics() {
        val results = listOf(
            LaunchCandidateResult(
                packageName = "com.example.first",
                launchComponent = "com.example.first/.MainActivity",
                attemptResults = listOf("first stayed on launcher"),
                observedLaunchSignal = false,
                snapshot = "lastWindowStateChangedPackage=com.android.launcher",
                activity = "mResumedActivity com.android.launcher/.Launcher",
                window = "mCurrentFocus com.android.launcher/.Launcher",
            ),
            LaunchCandidateResult(
                packageName = "com.example.second",
                launchComponent = "com.example.second/.MainActivity",
                attemptResults = listOf("second reached foreground"),
                observedLaunchSignal = true,
                snapshot = "lastWindowStateChangedPackage=com.example.second",
                activity = "mResumedActivity com.example.second/.MainActivity",
                window = "mCurrentFocus com.example.second/.MainActivity",
            ),
        )

        val selected = selectFirstObservedLaunchCandidate(
            purpose = "synthetic fallback regression",
            launchResults = results.asSequence(),
        )

        assertEquals("com.example.second", selected.packageName)
        assertTrue(
            "Expected failed candidate diagnostics to be retained. diagnostics=${selected.priorCandidateDiagnostics}",
            selected.priorCandidateDiagnostics.contains("com.example.first") &&
                selected.priorCandidateDiagnostics.contains("first stayed on launcher") &&
                selected.priorCandidateDiagnostics.contains("com.android.launcher"),
        )
    }

    @Test
    fun selectedAppForegroundBeforeServiceConnects_launchesBlockActivityAfterServiceConnects() = runBlocking {
        val blockedPackage = resolveLaunchablePackages().first()
        disableAccessibilityServiceIfEnabled()
        waitUntil("KeepAccessibilityService should be disabled before connect catch-up setup", UI_TIMEOUT_MS) {
            !isAccessibilityServiceEnabled()
        }
        configureManualKeepBlock(blockedPackage)

        launchPackage(blockedPackage)
        waitForPackageForeground(
            packageName = blockedPackage,
            message = "Expected $blockedPackage to be foreground before Accessibility service reconnects",
        )
        KeepAccessibilityServiceDebugState.reset(context)

        setAccessibilityServiceEnabled(enabled = true)
        waitUntil("KeepAccessibilityService should be enabled without leaving the blocked foreground app", UI_TIMEOUT_MS) {
            isAccessibilityServiceEnabled()
        }
        waitForServiceStatePropagation()
        waitForServiceToObserveSelectedPackage(blockedPackage)

        waitUntil(
            message = "Expected KeepAccessibilityService to catch up and request BlockActivity for foreground $blockedPackage after service connection",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            KeepAccessibilityServiceDebugState.read(context).lastLaunchedBlockPackage == blockedPackage
        }
    }

    @Test
    fun selectedAppWithManualKeep_launchesBlockActivity() = runBlocking {
        val blockedPackage = launchFirstConfiguredPackage(
            purpose = "manual Keep selected-app block smoke",
            configureCandidate = { candidate -> configureManualKeepBlock(candidate) },
            waitForCandidateReady = { candidate -> waitForServiceToObserveSelectedPackage(candidate) },
        )
        waitForWindowEvent(blockedPackage)

        waitUntil(
            message = "Expected KeepAccessibilityService to request BlockActivity when $blockedPackage launches",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            KeepAccessibilityServiceDebugState.read(context).lastLaunchedBlockPackage == blockedPackage
        }
    }

    @Test
    fun activeRoutineWithoutManualKeep_launchesBlockActivityWithRoutineAttribution() = runBlocking {
        var routineId = ROUTINE_RUNTIME_TEST_ID
        val blockedPackage = launchFirstConfiguredPackage(
            purpose = "active routine foreground block smoke",
            configureCandidate = { candidate -> routineId = configureActiveRoutineBlock(candidate) },
        )
        waitForWindowEvent(blockedPackage)

        waitUntil(
            message = "Expected active Routine to request BlockActivity with routine attribution for $blockedPackage",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            snapshot.lastLaunchedBlockPackage == blockedPackage &&
                snapshot.lastLaunchedBlockSource == AnalyticsBlockSource.ROUTINE &&
                snapshot.lastLaunchedRoutineId == routineId.toString()
        }
    }

    @Test
    fun foregroundAppBecomesBlockedWhenRoutineStartTimeArrives() = runBlocking {
        val blockedPackage = resolveLaunchablePackages().first()
        launchPackage(blockedPackage)
        waitForPackageForeground(
            packageName = blockedPackage,
            message = "Expected $blockedPackage to be foreground before the routine start time arrives",
        )
        resetDebugStateRetainingConnectionFlag()

        val routineId = configureFutureRoutineBlock(blockedPackage)
        waitForServiceStatePropagation()

        waitUntil(
            message = "Expected foreground $blockedPackage to be re-evaluated and blocked when the routine start time arrives",
            timeoutMs = ROUTINE_START_REEVALUATION_TIMEOUT_MS,
        ) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            snapshot.lastLaunchedBlockPackage == blockedPackage &&
                snapshot.lastLaunchedBlockSource == AnalyticsBlockSource.ROUTINE &&
                snapshot.lastLaunchedRoutineId == routineId.toString()
        }
    }

    @Test
    fun activeAllDayGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution() = runBlocking {
        var goalLockId = GOAL_LOCK_RUNTIME_TEST_ID
        val blockedPackage = launchFirstConfiguredPackage(
            purpose = "all-day Goal Lock block smoke",
            configureCandidate = { candidate -> goalLockId = configureAllDayGoalLockBlock(candidate) },
        )
        waitForWindowEvent(blockedPackage)

        waitUntil(
            message = "Expected KeepAccessibilityService to request BlockActivity with goal-lock attribution for $blockedPackage",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            snapshot.lastLaunchedBlockPackage == blockedPackage &&
                snapshot.lastLaunchedBlockSource == AnalyticsBlockSource.GOAL_LOCK &&
                snapshot.lastLaunchedGoalLockId == goalLockId.toString()
        }
    }

    @Test
    fun activeScheduledGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution() = runBlocking {
        var goalLockId = GOAL_LOCK_RUNTIME_TEST_ID
        val blockedPackage = launchFirstConfiguredPackage(
            purpose = "scheduled Goal Lock block smoke",
            configureCandidate = { candidate -> goalLockId = configureScheduledGoalLockBlock(candidate) },
        )
        waitForWindowEvent(blockedPackage)

        waitUntil(
            message = "Expected KeepAccessibilityService to request BlockActivity with scheduled goal-lock attribution for $blockedPackage",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            snapshot.lastLaunchedBlockPackage == blockedPackage &&
                snapshot.lastLaunchedBlockSource == AnalyticsBlockSource.GOAL_LOCK &&
                snapshot.lastLaunchedGoalLockId == goalLockId.toString()
        }
    }

    @Test
    fun activeParentModeWithoutManualKeep_launchesBlockActivityWithParentModeAttribution() = runBlocking {
        val blockedPackage = launchFirstConfiguredPackage(
            purpose = "active Parent Mode block smoke",
            configureCandidate = { candidate -> configureActiveParentModeBlock(candidate) },
            pressHomeBeforeLaunch = false,
        )
        waitForWindowEvent(blockedPackage)

        waitUntil(
            message = "Expected KeepAccessibilityService to observe active Parent Mode and request BlockActivity with parent-mode attribution for $blockedPackage",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            snapshot.observedParentModeState == "active" &&
                snapshot.observedParentModeAllowedAppCount == 1 &&
                snapshot.lastLaunchedBlockPackage == blockedPackage &&
                snapshot.lastLaunchedBlockSource == AnalyticsBlockSource.PARENT_MODE
        }
    }

    @Test
    fun expiredActiveParentModeWithoutManualKeep_blocksPreviouslyAllowedAppWithExpiredEvidence() = runBlocking {
        val allowedPackage = launchFirstConfiguredPackage(
            purpose = "expired Parent Mode previously-allowed app block smoke",
            configureCandidate = { candidate -> configureExpiredActiveParentMode(candidate) },
            pressHomeBeforeLaunch = false,
        )
        waitForWindowEvent(allowedPackage)

        waitUntil(
            message = "Expected expired Parent Mode to block previously allowed app with expired state evidence for $allowedPackage",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            snapshot.observedParentModeState == "expired" &&
                snapshot.observedParentModeAllowedAppCount == 1 &&
                snapshot.lastLaunchedBlockPackage == allowedPackage &&
                snapshot.lastLaunchedBlockSource == AnalyticsBlockSource.PARENT_MODE
        }
    }

    @Test
    fun expiredGoalLockWithoutManualKeep_keepsTargetForegroundWithoutGoalLockAttribution() = runBlocking {
        val blockedPackage = launchFirstConfiguredPackage(
            purpose = "expired Goal Lock foreground smoke",
            configureCandidate = { candidate -> configureExpiredGoalLockBlock(candidate) },
        )
        waitForWindowEvent(blockedPackage)
        waitForPackageForeground(
            packageName = blockedPackage,
            message = "Expected expired Goal Lock target to stay foreground without manual Keep or active Goal Lock",
        )

        Thread.sleep(1_000)

        val snapshot = KeepAccessibilityServiceDebugState.read(context)
        assertFalse(
            "Did not expect expired Goal Lock to request BlockActivity. snapshot=$snapshot",
            snapshot.lastLaunchedBlockPackage == blockedPackage ||
                snapshot.lastLaunchedBlockSource == AnalyticsBlockSource.GOAL_LOCK,
        )
    }

    @Test
    fun emergencyUnlockActive_keepsSelectedAppForegroundInsteadOfLaunchingBlockActivity() = runBlocking {
        val bypassPackage = resolveLaunchablePackages().last()
        configureManualKeepBlock(bypassPackage)
        configureEmergencyUnlock(bypassPackage)
        waitForServiceStatePropagation()
        waitForServiceToObserveSelectedPackage(bypassPackage)
        waitForServiceToObserveEmergencyUnlockPackage(bypassPackage)

        launchPackage(bypassPackage)
        waitForPackageForeground(
            packageName = bypassPackage,
            message = "Expected $bypassPackage to stay foreground while emergency unlock is active",
        )

        Thread.sleep(1_000)

        val debugSnapshot = KeepAccessibilityServiceDebugState.read(context)
        val launchedBlockedPackage = debugSnapshot.lastLaunchedBlockPackage
        assertFalse(
            "Did not expect KeepAccessibilityService to request BlockActivity while emergency unlock is active. snapshot=$debugSnapshot",
            launchedBlockedPackage == bypassPackage,
        )
    }

    @Test
    fun emergencyUnlockStoredExpiry_syncsCountdownNotificationAfterServiceSnapshot() = runBlocking {
        val bypassPackage = resolveLaunchablePackages().last()
        grantPostNotificationsPermission()
        configureManualKeepBlock(bypassPackage)
        val expireTimeMillis = configureEmergencyUnlock(bypassPackage)

        waitForServiceStatePropagation()
        waitForServiceToObserveEmergencyUnlockPackage(bypassPackage)

        waitUntil(
            message = "Expected KeepAccessibilityService to recreate the emergency-unlock countdown notification from stored expireTimeMillis after service snapshot",
            timeoutMs = SERVICE_PROPAGATION_TIMEOUT_MS,
        ) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            snapshot.lastCountdownNotificationExpireTimeMillis == expireTimeMillis &&
                snapshot.lastCountdownNotificationPostResult == EmergencyUnlockNotificationPostResult.Posted.name
        }

        val snapshot = KeepAccessibilityServiceDebugState.read(context)
        assertEquals(expireTimeMillis, snapshot.observedEmergencyUnlockExpireTimeMillis)
        assertEquals(expireTimeMillis, snapshot.lastCountdownNotificationExpireTimeMillis)
        assertEquals(
            EmergencyUnlockNotificationPostResult.Posted.name,
            snapshot.lastCountdownNotificationPostResult,
        )
    }

    @Test
    fun uninstallAttemptWithPreventUninstallEnabled_dismissesDeleteSurface() = runBlocking {
        configurePreventUninstall(enabled = true)
        waitForServiceStatePropagation()
        waitForPreventUninstallPropagation(expected = true)

        launchSelfUninstallFlow()

        waitUntil(
            message = "Expected KeepAccessibilityService to dismiss the self-uninstall surface when prevent_uninstall is enabled",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            KeepAccessibilityServiceDebugState.read(context).lastDismissedUninstallPackage == appPackage
        }
    }

    @Test
    fun appInfoScreenWithPreventUninstallEnabled_staysVisibleBeforeDeleteConfirmation() = runBlocking {
        configurePreventUninstall(enabled = true)
        waitForServiceStatePropagation()
        waitForPreventUninstallPropagation(expected = true)

        launchSelfAppInfoScreen(requireUninstallButton = false)
        waitForPackageForeground(
            packageName = SETTINGS_PACKAGE,
            message = "Expected the app info screen to stay foreground before uninstall confirmation",
        )

        Thread.sleep(750)

        assertTrue(
            "Expected no uninstall dismissal record before the delete confirmation surface is opened",
            KeepAccessibilityServiceDebugState.read(context).lastDismissedUninstallPackage == null,
        )
        assertTrue(
            "Expected the app info screen to stay visible before tapping uninstall",
            isPackageForeground(SETTINGS_PACKAGE),
        )
    }

    @Test
    fun uninstallAttemptWithPreventUninstallDisabled_keepsDeleteSurfaceVisible() = runBlocking {
        configurePreventUninstall(enabled = false)
        waitForServiceStatePropagation()
        waitForPreventUninstallPropagation(expected = false)

        launchSelfUninstallFlow()

        waitUntil(
            message = "Expected the uninstall surface to stay visible when prevent_uninstall is disabled",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            isUninstallSurfaceForeground()
        }
        assertTrue(
            "Expected no uninstall dismissal record when prevent_uninstall is disabled",
            KeepAccessibilityServiceDebugState.read(context).lastDismissedUninstallPackage == null,
        )
    }

    @Test
    fun cleanupRestoresAccessibilityServiceWhenItWasInitiallyDisabled() = runBlocking {
        assertTrue(
            "Expected test setup to enable KeepAccessibilityService before cleanup verification. ${accessibilityDiagnostics()}",
            isAccessibilityServiceEnabled(),
        )

        restoreAccessibilityServiceState(initiallyEnabled = false)

        waitUntil(
            message = "Expected cleanup helper to disable KeepAccessibilityService when it was initially disabled. ${accessibilityDiagnostics()}",
            timeoutMs = SERVICE_PROPAGATION_TIMEOUT_MS,
        ) {
            !isAccessibilityServiceEnabled()
        }
        assertFalse(
            "Expected KeepAccessibilityService to be disabled after cleanup restoration. ${accessibilityDiagnostics()}",
            isAccessibilityServiceEnabled(),
        )
    }

    private suspend fun configureManualKeepBlock(packageName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.SELECTED_APP_PACKAGES] = setOf(packageName)
            preferences[PreferencesKey.IS_KEEP] = true
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
    }

    private suspend fun configureAllDayGoalLockBlock(packageName: String): Long {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
            preferences[PreferencesKey.IS_KEEP] = false
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
        goalLockRepository().create(
            GoalLock(
                id = GOAL_LOCK_RUNTIME_TEST_ID,
                goalName = "Runtime QA",
                startDate = LocalDate.now().minusDays(1),
                endDate = LocalDate.now().plusDays(1),
                lockMode = GoalLockMode.AllDay,
                selectedPackages = setOf(packageName),
                status = GoalLockStoredStatus.Active,
            ),
        )
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
        return GOAL_LOCK_RUNTIME_TEST_ID
    }

    private suspend fun configureScheduledGoalLockBlock(packageName: String): Long {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
            preferences[PreferencesKey.IS_KEEP] = false
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
        goalLockRepository().create(
            GoalLock(
                id = GOAL_LOCK_RUNTIME_TEST_ID,
                goalName = "Runtime QA",
                startDate = LocalDate.now().minusDays(1),
                endDate = LocalDate.now().plusDays(1),
                lockMode = GoalLockMode.Scheduled(
                    repeatDays = setOf(LocalDate.now().dayOfWeek),
                    startTime = LocalTime.MIN,
                    endTime = LocalTime.of(23, 59, 59),
                ),
                selectedPackages = setOf(packageName),
                status = GoalLockStoredStatus.Active,
            ),
        )
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
        return GOAL_LOCK_RUNTIME_TEST_ID
    }

    private suspend fun configureExpiredGoalLockBlock(packageName: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
            preferences[PreferencesKey.IS_KEEP] = false
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
        goalLockRepository().create(
            GoalLock(
                id = GOAL_LOCK_RUNTIME_TEST_ID,
                goalName = "Runtime QA",
                startDate = LocalDate.now().minusDays(7),
                endDate = LocalDate.now().minusDays(1),
                lockMode = GoalLockMode.AllDay,
                selectedPackages = setOf(packageName),
                status = GoalLockStoredStatus.Active,
            ),
        )
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
    }

    private suspend fun configureActiveParentModeBlock(blockedPackage: String) {
        val nowMillis = System.currentTimeMillis()
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
            preferences[PreferencesKey.IS_KEEP] = false
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
            preferences[PreferencesKey.PARENT_MODE_STARTED_AT] = nowMillis - 1_000L
            preferences[PreferencesKey.PARENT_MODE_EXPIRES_AT] = nowMillis + PARENT_MODE_RUNTIME_WINDOW_MS
            preferences[PreferencesKey.PARENT_MODE_DURATION_MINUTES] = 10
            preferences[PreferencesKey.PARENT_MODE_ALLOWED_APPS] = setOf(appPackage)
            preferences[PreferencesKey.PARENT_MODE_STATE] = "active"
        }
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
    }

    private suspend fun configureExpiredActiveParentMode(allowedPackage: String) {
        val nowMillis = System.currentTimeMillis()
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
            preferences[PreferencesKey.IS_KEEP] = false
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
            preferences[PreferencesKey.PARENT_MODE_STARTED_AT] = nowMillis - PARENT_MODE_RUNTIME_WINDOW_MS
            preferences[PreferencesKey.PARENT_MODE_EXPIRES_AT] = nowMillis - 1_000L
            preferences[PreferencesKey.PARENT_MODE_DURATION_MINUTES] = 10
            preferences[PreferencesKey.PARENT_MODE_ALLOWED_APPS] = setOf(allowedPackage)
            preferences[PreferencesKey.PARENT_MODE_STATE] = "active"
        }
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
    }

    private fun goalLockRepository(): GoalLockRepository = EntryPointAccessors.fromApplication(
        context.applicationContext,
        KeepAccessibilityService.RoutineRuntimeEntryPoint::class.java,
    ).goalLockRepository()

    private fun routineRepository() = EntryPointAccessors.fromApplication(
        context.applicationContext,
        KeepAccessibilityService.RoutineRuntimeEntryPoint::class.java,
    ).routineRepository()

    private suspend fun configureActiveRoutineBlock(blockedPackage: String): Long {
        val now = LocalDateTime.now()
        val routine = RoutineModel(
            id = ROUTINE_RUNTIME_TEST_ID,
            name = "Runtime active routine",
            startTime = now.minusMinutes(5).toKotlinTime(),
            endTime = now.plusMinutes(55).toKotlinTime(),
            repeatDays = listOf(now.dayOfWeek).toRepeatDaysBinary(),
            lockApplications = listOf(blockedPackage),
            isEnabled = true,
        )
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
            preferences[PreferencesKey.IS_KEEP] = false
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
        routineRepository().insert(routine)
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
        return routine.id
    }

    private suspend fun configureFutureRoutineBlock(blockedPackage: String): Long {
        val startTime = LocalDateTime.now().plusSeconds(3)
        val routine = RoutineModel(
            id = ROUTINE_RUNTIME_TEST_ID,
            name = "Runtime future-start routine",
            startTime = startTime.toKotlinTimeWithSeconds(),
            endTime = startTime.plusMinutes(30).toKotlinTimeWithSeconds(),
            repeatDays = listOf(startTime.dayOfWeek).toRepeatDaysBinary(),
            lockApplications = listOf(blockedPackage),
            isEnabled = true,
        )
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
            preferences[PreferencesKey.IS_KEEP] = false
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
        routineRepository().insert(routine)
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
        return routine.id
    }

    private fun LocalDateTime.toKotlinTime(): KotlinLocalTime =
        KotlinLocalTime(hour = hour, minute = minute)

    private fun LocalDateTime.toKotlinTimeWithSeconds(): KotlinLocalTime =
        KotlinLocalTime(hour = hour, minute = minute, second = second)

    private suspend fun configureEmergencyUnlock(packageName: String): Long {
        val expireTimeMillis = System.currentTimeMillis() + EMERGENCY_UNLOCK_WINDOW_MS
        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS] = setOf(packageName)
            preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] = expireTimeMillis
        }
        EmergencyUnlockState.current = EmergencyUnlockData(
            unlockedApps = setOf(packageName),
            expireTimeMillis = expireTimeMillis,
        )
        return expireTimeMillis
    }

    private suspend fun configurePreventUninstall(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.PREVENT_UNINSTALL] = enabled
        }
    }

    private suspend fun clearAccessibilityBlockState() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.SELECTED_APP_PACKAGES)
            preferences.remove(PreferencesKey.IS_KEEP)
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.PREVENT_UNINSTALL)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
            preferences.remove(PreferencesKey.PARENT_MODE_STARTED_AT)
            preferences.remove(PreferencesKey.PARENT_MODE_EXPIRES_AT)
            preferences.remove(PreferencesKey.PARENT_MODE_DURATION_MINUTES)
            preferences.remove(PreferencesKey.PARENT_MODE_ALLOWED_APPS)
            preferences.remove(PreferencesKey.PARENT_MODE_STATE)
        }
        clearGoalLockRuntimeBlock()
        clearRoutineRuntimeBlock()
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
        EmergencyUnlockNotificationHelper(context).cancel()
        KeepAccessibilityServiceDebugState.reset(context)
    }

    private suspend fun clearRoutineRuntimeBlock() {
        runCatching { routineRepository().deleteById(ROUTINE_RUNTIME_TEST_ID) }
    }

    private fun clearGoalLockRuntimeBlock() {
        runCatching {
            goalLockRepository().create(
                GoalLock(
                    id = GOAL_LOCK_RUNTIME_TEST_ID,
                    goalName = "Runtime QA",
                    startDate = LocalDate.now().minusDays(1),
                    endDate = LocalDate.now().minusDays(1),
                    lockMode = GoalLockMode.AllDay,
                    selectedPackages = emptySet(),
                    status = GoalLockStoredStatus.Completed,
                ),
            )
        }
    }

    private fun grantPostNotificationsPermission() {
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant $appPackage android.permission.POST_NOTIFICATIONS",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "appops set $appPackage POST_NOTIFICATION allow",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "appops set --uid $appPackage POST_NOTIFICATION allow",
        ).close()
        waitUntil("POST_NOTIFICATIONS should be enabled for countdown notification test setup") {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        // Runtime permission changes can restart or unbind the target process on emulator images.
        // Rebind the AccessibilityService before asserting service-owned notification sync.
        primeAppProcess()
        setAccessibilityServiceEnabled(enabled = false)
        waitUntil("KeepAccessibilityService should be disabled before notification-permission rebind") {
            !isAccessibilityServiceEnabled()
        }
        KeepAccessibilityServiceDebugState.reset(context)
        setAccessibilityServiceEnabled(enabled = true)
        waitUntil("KeepAccessibilityService should be enabled after notification-permission rebind") {
            isAccessibilityServiceEnabled()
        }
        waitForServiceStatePropagation()
    }

    private fun resolveLaunchablePackages(): List<String> {
        val packages = LAUNCHABLE_PACKAGE_CANDIDATES.filter { candidate ->
            context.packageManager.getLaunchIntentForPackage(candidate) != null
        }
        assertTrue(
            "Expected at least two launchable system packages for Accessibility runtime tests, found $packages",
            packages.size >= 2,
        )
        return packages.take(2)
    }

    private data class LaunchCandidateResult(
        val packageName: String,
        val launchComponent: String?,
        val attemptResults: List<String>,
        val observedLaunchSignal: Boolean,
        val snapshot: String,
        val activity: String,
        val window: String,
        val priorCandidateDiagnostics: String = "",
    ) {
        fun diagnosticSummary(): String = buildString {
            append("package=").append(packageName)
            append("; launchComponent=").append(launchComponent)
            append("; observedLaunchSignal=").append(observedLaunchSignal)
            append("; attemptResults=").append(attemptResults.joinToString(" || "))
            append("; snapshot=").append(snapshot)
            append("; activity=").append(activity)
            append("; window=").append(window)
        }
    }

    private fun selectFirstObservedLaunchCandidate(
        purpose: String,
        launchResults: Sequence<LaunchCandidateResult>,
    ): LaunchCandidateResult {
        val failedCandidateDiagnostics = mutableListOf<String>()
        launchResults.forEach { result ->
            if (result.observedLaunchSignal) {
                return result.copy(
                    priorCandidateDiagnostics = failedCandidateDiagnostics.joinToString(separator = "\n"),
                )
            }
            failedCandidateDiagnostics += result.diagnosticSummary()
        }

        fail(
            "Expected at least one launch candidate to produce a foreground/window/block signal for $purpose. " +
                "candidateDiagnostics=${failedCandidateDiagnostics.joinToString(separator = "\n")}",
        )
        throw AssertionError("unreachable")
    }

    private suspend fun launchFirstConfiguredPackage(
        purpose: String,
        configureCandidate: suspend (String) -> Unit,
        waitForCandidateReady: suspend (String) -> Unit = {},
        pressHomeBeforeLaunch: Boolean = true,
    ): String {
        val launchResults = mutableListOf<LaunchCandidateResult>()
        resolveLaunchablePackages().forEach { candidate ->
            device.pressHome()
            resetDebugStateRetainingConnectionFlag()
            configureCandidate(candidate)
            waitForServiceStatePropagation()
            waitForCandidateReady(candidate)
            val result = tryLaunchPackage(
                packageName = candidate,
                pressHomeBeforeLaunch = pressHomeBeforeLaunch,
            )
            launchResults += result
            if (result.observedLaunchSignal) {
                return selectFirstObservedLaunchCandidate(
                    purpose = purpose,
                    launchResults = launchResults.asSequence(),
                ).packageName
            }
        }
        return selectFirstObservedLaunchCandidate(
            purpose = purpose,
            launchResults = launchResults.asSequence(),
        ).packageName
    }

    private fun launchPackage(packageName: String) {
        selectFirstObservedLaunchCandidate(
            purpose = "specific package launch for $packageName",
            launchResults = sequenceOf(tryLaunchPackage(packageName)),
        )
    }

    private fun tryLaunchPackage(
        packageName: String,
        pressHomeBeforeLaunch: Boolean = true,
    ): LaunchCandidateResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            return LaunchCandidateResult(
                packageName = packageName,
                launchComponent = null,
                attemptResults = listOf("missing launch intent"),
                observedLaunchSignal = false,
                snapshot = KeepAccessibilityServiceDebugState.read(context).toString(),
                activity = resumedActivityDump(),
                window = focusedWindowDump(),
            )
        }

        val launchComponent = launchIntent.component?.flattenToShortString()
        val attemptResults = mutableListOf<String>()
        var observedLaunchSignal = false
        repeat(LAUNCH_ATTEMPTS) { attempt ->
            if (observedLaunchSignal) {
                return@repeat
            }
            if (packageName != appPackage) {
                shell("am force-stop $packageName")
            }
            if (pressHomeBeforeLaunch) {
                device.pressHome()
            }
            val launchResult = if (launchComponent != null) {
                shell("am start -W -n $launchComponent")
            } else {
                context.startActivity(
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                "started via context.startActivity"
            }
            attemptResults += "attempt=${attempt + 1}; result=${launchResult.trim()}"
            if (waitForLaunchSignal(packageName, LAUNCH_SETTLE_TIMEOUT_MS)) {
                observedLaunchSignal = true
                return@repeat
            }
        }

        return LaunchCandidateResult(
            packageName = packageName,
            launchComponent = launchComponent,
            attemptResults = attemptResults,
            observedLaunchSignal = observedLaunchSignal,
            snapshot = KeepAccessibilityServiceDebugState.read(context).toString(),
            activity = resumedActivityDump(),
            window = focusedWindowDump(),
        )
    }

    private fun waitForLaunchSignal(packageName: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            if (snapshot.lastWindowStateChangedPackage == packageName ||
                snapshot.lastLaunchedBlockPackage == packageName ||
                isPackageForeground(packageName)
            ) {
                return true
            }
            Thread.sleep(250)
        }
        return false
    }

    private fun launchSelfUninstallFlow() {
        KNOWN_UNINSTALL_PACKAGES.forEach { packageName -> shell("am force-stop $packageName") }
        launchSelfAppInfoScreen(requireUninstallButton = false)
        val uninstallButton = findUninstallButton()
        if (uninstallButton != null) {
            uninstallButton.click()
        } else {
            launchDirectSelfDeleteIntent()
        }
        waitUntil(
            message = "Expected package installer uninstall confirmation for $appPackage to become visible",
            timeoutMs = PACKAGE_VISIBILITY_TIMEOUT_MS,
        ) {
            isUninstallSurfaceForeground()
        }
    }

    private fun launchDirectSelfDeleteIntent() {
        device.pressHome()
        shell("am start -W -a android.intent.action.DELETE -d package:$appPackage")
    }

    private fun launchSelfAppInfoScreen(requireUninstallButton: Boolean = true) {
        shell("am force-stop $SETTINGS_PACKAGE")
        device.pressHome()
        val launchResult = shell(
            "am start -W -a ${Settings.ACTION_APPLICATION_DETAILS_SETTINGS} -d package:$appPackage",
        )
        waitForPackageForeground(
            packageName = SETTINGS_PACKAGE,
            message = "Expected Settings app info screen to foreground for $appPackage. launchResult=$launchResult",
        )
        if (requireUninstallButton) {
            waitForUninstallButton()
        }
    }

    private fun waitForUninstallButton(): androidx.test.uiautomator.UiObject2 {
        waitUntil(
            message = "Expected app info screen to expose an uninstall/delete action for $appPackage. visibleTexts=${visibleSettingsTexts()}",
            timeoutMs = UI_TIMEOUT_MS,
        ) {
            findUninstallButton() != null
        }
        return findUninstallButton() ?: run {
            fail("Could not find uninstall/delete action for $appPackage from app info screen. visibleTexts=${visibleSettingsTexts()}")
            throw AssertionError("unreachable")
        }
    }

    private fun findUninstallButton(): androidx.test.uiautomator.UiObject2? {
        val selectors = listOf(
            By.text(UNINSTALL_ACTION_PATTERN),
            By.desc(UNINSTALL_ACTION_PATTERN),
            By.textContains("Uninstall"),
            By.textContains("Delete"),
            By.textContains("Remove"),
        )
        return selectors.firstNotNullOfOrNull { selector ->
            device.findObject(selector)?.takeIf { it.isEnabled }
        }
    }

    private fun visibleSettingsTexts(): String =
        device.findObjects(By.pkg(SETTINGS_PACKAGE))
            .mapNotNull { node -> node.text?.takeIf { it.isNotBlank() } }
            .distinct()
            .take(30)
            .joinToString(" | ")

    private fun primeAppProcess() {
        launchPackage(appPackage)
        waitUntil("StopIt app should leave the stopped state before Accessibility service setup") {
            !shell("dumpsys package $appPackage").contains("stopped=true")
        }
    }

    private fun enableAccessibilityServiceIfNeeded() {
        if (isAccessibilityServiceEnabled()) return

        setAccessibilityServiceEnabled(enabled = true)
        waitUntil(
            message = "Expected secure-settings setup to enable KeepAccessibilityService. ${accessibilityDiagnostics()}",
            timeoutMs = SERVICE_PROPAGATION_TIMEOUT_MS,
        ) {
            isAccessibilityServiceEnabled()
        }
    }

    private fun disableAccessibilityServiceIfEnabled() {
        if (!isAccessibilityServiceEnabled()) return

        val before = accessibilityDiagnostics()
        setAccessibilityServiceEnabled(enabled = false)

        waitUntil(
            message = "Expected KeepAccessibilityService to be disabled. before=$before; after=${accessibilityDiagnostics()}",
            timeoutMs = SERVICE_PROPAGATION_TIMEOUT_MS,
        ) {
            !isAccessibilityServiceEnabled()
        }
    }

    private fun restoreAccessibilityServiceState(initiallyEnabled: Boolean) {
        if (initiallyEnabled) return
        disableAccessibilityServiceIfEnabled()
    }

    private fun setAccessibilityServiceEnabled(enabled: Boolean) {
        val currentServices = normalizeSecureSetting(shell("settings get secure enabled_accessibility_services"))
        val retainedServices = currentServices
            .split(':')
            .filter { it.isNotBlank() && it != serviceComponent }
            .toMutableList()

        if (enabled) {
            retainedServices += serviceComponent
        }

        if (retainedServices.isEmpty()) {
            shell("settings delete secure enabled_accessibility_services")
            shell("settings put secure accessibility_enabled 0")
        } else {
            shell("settings put secure enabled_accessibility_services ${retainedServices.joinToString(":")}")
            shell("settings put secure accessibility_enabled 1")
        }
    }

    private fun accessibilityDiagnostics(): String {
        val snapshot = KeepAccessibilityServiceDebugState.read(context)
        val accessibilityEnabled = normalizeSecureSetting(shell("settings get secure accessibility_enabled"))
        val enabledServices = normalizeSecureSetting(shell("settings get secure enabled_accessibility_services"))
        val accessibilityDump = shell("""dumpsys accessibility | grep -n 'Bound services\|Enabled services\|Binding services\|Crashed services' -A1 -B1""").trim()
        return "snapshot=$snapshot; accessibility_enabled=$accessibilityEnabled; enabled_accessibility_services=$enabledServices; accessibilityDump=$accessibilityDump"
    }

    private fun normalizeSecureSetting(rawValue: String): String =
        rawValue.trim().takeUnless { it == "null" } ?: ""
    private fun isAccessibilityServiceEnabled(): Boolean =
        shell("dumpsys accessibility").contains("Enabled services:{{$serviceComponent}}")

    private fun waitForServiceStatePropagation() {
        val deadline = System.currentTimeMillis() + SERVICE_PROPAGATION_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (KeepAccessibilityServiceDebugState.read(context).isServiceConnected) {
                return
            }
            Thread.sleep(100)
        }

        val snapshot = KeepAccessibilityServiceDebugState.read(context)
        val accessibilityEnabled = shell("settings get secure accessibility_enabled").trim()
        val enabledServices = shell("settings get secure enabled_accessibility_services").trim()
        val packageState = shell("dumpsys package $appPackage | grep -n 'User 0:' -A2 | head -n 3").trim()
        val accessibilityDump = shell("""dumpsys accessibility | grep -n 'Bound services\|Enabled services\|Binding services\|Crashed services' -A1 -B1""").trim()
        fail(
            "KeepAccessibilityService should bind before runtime assertions. " +
                "snapshot=$snapshot; accessibility_enabled=$accessibilityEnabled; " +
                "enabled_accessibility_services=$enabledServices; packageState=$packageState; " +
                "accessibilityDump=$accessibilityDump",
        )
    }

    private fun waitForServiceToObserveSelectedPackage(packageName: String) {
        waitUntil("KeepAccessibilityService should observe selected foreground packages before launch", SERVICE_PROPAGATION_TIMEOUT_MS) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            snapshot.observedIsKeep && snapshot.observedSelectedAppPackages.contains(packageName)
        }
    }

    private fun waitForServiceToObserveEmergencyUnlockPackage(packageName: String) {
        waitUntil("KeepAccessibilityService should observe emergency unlock state before launch", SERVICE_PROPAGATION_TIMEOUT_MS) {
            KeepAccessibilityServiceDebugState.read(context)
                .observedEmergencyUnlockApps
                .contains(packageName)
        }
    }

    private fun waitForPreventUninstallPropagation(expected: Boolean) {
        waitUntil("KeepAccessibilityService should observe prevent_uninstall=$expected before uninstall assertions", SERVICE_PROPAGATION_TIMEOUT_MS) {
            KeepAccessibilityServiceDebugState.read(context).observedPreventUninstall == expected
        }
    }

    private fun waitForWindowEvent(packageName: String) {
        waitUntil(
            message = "KeepAccessibilityService should receive or process a window change event for $packageName. snapshot=${KeepAccessibilityServiceDebugState.read(context)}",
            timeoutMs = SERVICE_PROPAGATION_TIMEOUT_MS,
        ) {
            val snapshot = KeepAccessibilityServiceDebugState.read(context)
            snapshot.lastWindowStateChangedPackage == packageName ||
                snapshot.lastLaunchedBlockPackage == packageName
        }
    }

    private fun waitForPackageVisible(
        packageName: String,
        message: String,
    ) {
        waitUntil(message, PACKAGE_VISIBILITY_TIMEOUT_MS) {
            device.hasObject(By.pkg(packageName).depth(0)) || isPackageForeground(packageName)
        }
    }

    private fun waitForPackageForeground(
        packageName: String,
        message: String,
    ) {
        waitUntil(message, PACKAGE_VISIBILITY_TIMEOUT_MS) {
            isPackageForeground(packageName)
        }
    }

    private fun isPackageForeground(packageName: String): Boolean {
        if (resumedActivityDump().contains("$packageName/")) {
            return true
        }

        return focusedWindowDump().contains(packageName)
    }

    private fun resumedActivityDump(): String =
        shell("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'").trim()

    private fun focusedWindowDump(): String =
        shell("dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'").trim()

    private fun isUninstallSurfaceForeground(): Boolean =
        KNOWN_UNINSTALL_PACKAGES.any(::isPackageForeground)

    private fun shell(command: String): String {
        return device.executeShellCommand(command)
    }

    private val appName: String
        get() = context.packageManager.getApplicationLabel(context.applicationInfo).toString()

    private val appPackage: String
        get() = context.packageName

    private val serviceComponent: String
        get() = "$appPackage/com.uiery.keep.service.KeepAccessibilityService"

    private fun resetDebugStateRetainingConnectionFlag() {
        val existingSnapshot = KeepAccessibilityServiceDebugState.read(context)
        KeepAccessibilityServiceDebugState.update(context) {
            it.copy(
                isServiceConnected = existingSnapshot.isServiceConnected,
                observedIsKeep = false,
                observedPreventUninstall = true,
                observedSelectedAppPackages = emptySet(),
                observedEmergencyUnlockApps = emptySet(),
                observedParentModeState = null,
                observedParentModeAllowedAppCount = 0,
                lastWindowStateChangedPackage = null,
                lastLaunchedBlockPackage = null,
                lastLaunchedBlockSource = null,
                lastLaunchedRoutineId = null,
                lastLaunchedGoalLockId = null,
                lastDismissedUninstallPackage = null,
            )
        }
    }

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

    private companion object {
        const val PACKAGE_VISIBILITY_TIMEOUT_MS = 5_000L
        const val UNINSTALL_DISMISS_TIMEOUT_MS = 12_000L
        const val EMERGENCY_UNLOCK_WINDOW_MS = 60_000L
        const val PARENT_MODE_RUNTIME_WINDOW_MS = 60_000L
        const val GOAL_LOCK_RUNTIME_TEST_ID = 417_001L
        const val ROUTINE_RUNTIME_TEST_ID = 609_001L
        const val UI_TIMEOUT_MS = 8_000L
        const val SERVICE_PROPAGATION_TIMEOUT_MS = 10_000L
        const val ROUTINE_START_REEVALUATION_TIMEOUT_MS = 20_000L
        const val LAUNCH_ATTEMPTS = 3
        const val LAUNCH_SETTLE_TIMEOUT_MS = 2_000L
        const val SETTINGS_PACKAGE = "com.android.settings"
        val UNINSTALL_ACTION_PATTERN: Pattern = Pattern.compile("(?i)(uninstall|delete|remove)(\\s+app)?")
        val KNOWN_UNINSTALL_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.samsung.android.packageinstaller",
            "com.android.vending",
        )
        val LAUNCHABLE_PACKAGE_CANDIDATES = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.android.settings",
        )
    }
}

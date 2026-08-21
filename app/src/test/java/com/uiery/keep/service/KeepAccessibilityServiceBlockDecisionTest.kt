package com.uiery.keep.service

import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.appselection.BlockExemptPackages
import com.uiery.keep.appselection.SensitiveAppRole
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
import com.uiery.keep.model.RoutineModel
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime as JavaLocalTime
import java.time.ZoneId

class KeepAccessibilityServiceBlockDecisionTest {
    @Test
    fun manualKeepSelectedPackageRequestsBlockActivity() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep",
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf("com.uiery.keep"),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = "com.uiery.keep",
                blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            ),
            request,
        )
    }

    @Test
    fun timedLockSelectedPackageUsesTimedLockSource() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep",
            prefs = AccessibilityBlockingPreferences(
                lockTime = "2026-05-27T10:05:00",
                selectedAppPackages = setOf("com.uiery.keep"),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = "com.uiery.keep",
                blockSource = AnalyticsBlockSource.TIMED_LOCK,
            ),
            request,
        )
    }

    @Test
    fun timedLockInstantDeadlineUsesTimedLockSource() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 5, 27, 10, 0)
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep",
            prefs = AccessibilityBlockingPreferences(
                lockTime = ManualLockTimePolicy.encodeDeadline(now.plusMinutes(5).atZone(zone).toInstant()),
                selectedAppPackages = setOf("com.uiery.keep"),
            ),
            cachedRoutines = emptyList(),
            now = now,
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = "com.uiery.keep",
                blockSource = AnalyticsBlockSource.TIMED_LOCK,
            ),
            request,
        )
    }

    @Test
    fun activeRoutineTargetUsesRoutineSourceWithoutManualSelection() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep.target",
            prefs = AccessibilityBlockingPreferences(),
            cachedRoutines = listOf(activeRoutine(id = 42L, targetPackage = "com.uiery.keep.target")),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = "com.uiery.keep.target",
                blockSource = AnalyticsBlockSource.ROUTINE,
                routineId = "42",
            ),
            request,
        )
    }

    @Test
    fun blockExemptPackageIsNeverBlockedByManualKeep() {
        val request = resolveForegroundBlockRequest(
            packageName = HOME_LAUNCHER,
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf(HOME_LAUNCHER),
                exemptPackages = BlockExemptPackages(homePackages = setOf(HOME_LAUNCHER)),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertNull(request)
    }

    @Test
    fun blockExemptPackageIsNeverBlockedByAnActiveRoutine() {
        val request = resolveForegroundBlockRequest(
            packageName = HOME_LAUNCHER,
            prefs = AccessibilityBlockingPreferences(
                exemptPackages = BlockExemptPackages(homePackages = setOf(HOME_LAUNCHER)),
            ),
            cachedRoutines = listOf(activeRoutine(id = 42L, targetPackage = HOME_LAUNCHER)),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertNull(request)
    }

    /**
     * Sensitive packages are blockable. The user picked Settings deliberately and was warned before
     * saving, so the lock has to honour it.
     */
    @Test
    fun sensitiveAppIsBlockedUnderASelfImposedLockWhenTheUserPickedIt() {
        val request = resolveForegroundBlockRequest(
            packageName = SETTINGS,
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf(SETTINGS),
                exemptPackages = BlockExemptPackages(sensitiveRoles = mapOf(SETTINGS to SensitiveAppRole.SETTINGS)),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = SETTINGS,
                blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            ),
            request,
        )
    }

    /** A sensitive package the user never picked is still not blocked. */
    @Test
    fun sensitiveAppStaysReachableWhenTheUserDidNotPickIt() {
        val request = resolveForegroundBlockRequest(
            packageName = SETTINGS,
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf(INSTAGRAM),
                exemptPackages = BlockExemptPackages(sensitiveRoles = mapOf(SETTINGS to SensitiveAppRole.SETTINGS)),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertNull(request)
    }

    /**
     * With the screen off Android brings the dialer's in-call activity to the front, which is a
     * window change like any other. Without this exemption a blocked dialer would put the block
     * screen over a ringing phone.
     */
    @Test
    fun blockedDialerIsNotBlockedWhileACallIsLive() {
        val request = resolveForegroundBlockRequest(
            packageName = DIALER,
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf(DIALER),
                exemptPackages = BlockExemptPackages(
                    sensitiveRoles = mapOf(DIALER to SensitiveAppRole.DIALER),
                ),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
            isCallInProgress = true,
        )

        assertNull(request)
    }

    /** Opening the dialer to place a call is the blocking the user asked for. */
    @Test
    fun blockedDialerIsStillBlockedWithNoCallInProgress() {
        val request = resolveForegroundBlockRequest(
            packageName = DIALER,
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf(DIALER),
                exemptPackages = BlockExemptPackages(
                    sensitiveRoles = mapOf(DIALER to SensitiveAppRole.DIALER),
                ),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
            isCallInProgress = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = DIALER,
                blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            ),
            request,
        )
    }

    /** A live call excuses the dialer, not everything else the user blocked. */
    @Test
    fun aLiveCallDoesNotUnblockOtherApps() {
        val request = resolveForegroundBlockRequest(
            packageName = INSTAGRAM,
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf(INSTAGRAM),
                exemptPackages = BlockExemptPackages(sensitiveRoles = mapOf(DIALER to SensitiveAppRole.DIALER)),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
            isCallInProgress = true,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = INSTAGRAM,
                blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            ),
            request,
        )
    }

    /**
     * Parent mode is a supervisor's allowlist, and sensitive packages are blockable for everyone,
     * so Settings the supervisor did not allow stays blocked.
     */
    @Test
    fun parentModeKeepsBlockingEssentialAppsItDidNotAllow() {
        val request = resolveForegroundBlockRequest(
            packageName = SETTINGS,
            prefs = AccessibilityBlockingPreferences(
                exemptPackages = BlockExemptPackages(
                    homePackages = setOf(HOME_LAUNCHER),
                    sensitiveRoles = mapOf(SETTINGS to SensitiveAppRole.SETTINGS),
                ),
            ),
            cachedRoutines = emptyList(),
            parentModeSession = activeParentModeSession(allowedApps = emptySet()),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = SETTINGS,
                blockSource = AnalyticsBlockSource.PARENT_MODE,
            ),
            request,
        )
    }

    /**
     * The person holding the phone during parent mode is not the person who set the lock up, so an
     * emergency call cannot depend on the parent having thought to put the dialer on the allowlist.
     * The self-control locks keep blocking outgoing calls — there the blocker and the caller are the
     * same person — but a supervised session cannot make that trade on someone else's behalf.
     */
    @Test
    fun parentModeKeepsTheDialerReachableForOutgoingEmergencyCalls() {
        val request = resolveForegroundBlockRequest(
            packageName = DIALER,
            prefs = AccessibilityBlockingPreferences(
                exemptPackages = BlockExemptPackages(
                    sensitiveRoles = mapOf(DIALER to SensitiveAppRole.DIALER),
                ),
            ),
            cachedRoutines = emptyList(),
            parentModeSession = activeParentModeSession(allowedApps = emptySet()),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
            isCallInProgress = false,
        )

        assertNull(request)
    }

    @Test
    fun expiredParentModeAlsoKeepsTheDialerReachable() {
        val request = resolveForegroundBlockRequest(
            packageName = DIALER,
            prefs = AccessibilityBlockingPreferences(
                exemptPackages = BlockExemptPackages(
                    sensitiveRoles = mapOf(DIALER to SensitiveAppRole.DIALER),
                ),
            ),
            cachedRoutines = emptyList(),
            parentModeSession = activeParentModeSession(allowedApps = setOf(DIALER)),
            // The session ran out an hour ago, which is when every app including the allowed ones
            // gets blocked. The dialer is the one that must not.
            now = LocalDateTime.of(2026, 5, 27, 12, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
            isCallInProgress = false,
        )

        assertNull(request)
    }

    /**
     * Without a parent mode session the existing trade stands: the user blocked their own dialer on
     * purpose, and only a live incoming call steps aside for it.
     */
    @Test
    fun selfControlLocksStillBlockOutgoingCallsToADeliberatelyBlockedDialer() {
        val request = resolveForegroundBlockRequest(
            packageName = DIALER,
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf(DIALER),
                exemptPackages = BlockExemptPackages(
                    sensitiveRoles = mapOf(DIALER to SensitiveAppRole.DIALER),
                ),
            ),
            cachedRoutines = emptyList(),
            parentModeSession = null,
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
            isCallInProgress = false,
        )

        assertEquals(
            ForegroundBlockRequest(packageName = DIALER, blockSource = AnalyticsBlockSource.MANUAL_KEEP),
            request,
        )
    }

    @Test
    fun parentModeStillCannotBlockTheHomeLauncher() {
        val request = resolveForegroundBlockRequest(
            packageName = HOME_LAUNCHER,
            prefs = AccessibilityBlockingPreferences(
                exemptPackages = BlockExemptPackages(homePackages = setOf(HOME_LAUNCHER)),
            ),
            cachedRoutines = emptyList(),
            parentModeSession = activeParentModeSession(allowedApps = emptySet()),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertNull(request)
    }

    @Test
    fun manualKeepSelectedPackageDoesNotAttachRoutineId() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep",
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf("com.uiery.keep"),
            ),
            cachedRoutines = listOf(activeRoutine(id = 42L, targetPackage = "com.uiery.keep.target")),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = "com.uiery.keep",
                blockSource = AnalyticsBlockSource.MANUAL_KEEP,
                routineId = null,
            ),
            request,
        )
    }

    @Test
    fun activeGoalLockTargetUsesGoalLockSourceWithoutManualSelection() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep.target",
            prefs = AccessibilityBlockingPreferences(),
            cachedRoutines = emptyList(),
            cachedGoalLocks = listOf(
                activeGoalLock(
                    id = 77L,
                    targetPackage = "com.uiery.keep.target",
                    mode = GoalLockMode.AllDay,
                ),
            ),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = "com.uiery.keep.target",
                blockSource = AnalyticsBlockSource.GOAL_LOCK,
                goalLockId = "77",
            ),
            request,
        )
    }

    @Test
    fun inactiveGoalLockDoesNotBlockSelectedTarget() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep.target",
            prefs = AccessibilityBlockingPreferences(),
            cachedRoutines = emptyList(),
            cachedGoalLocks = listOf(
                activeGoalLock(
                    id = 77L,
                    targetPackage = "com.uiery.keep.target",
                    mode = GoalLockMode.Scheduled(
                        repeatDays = setOf(DayOfWeek.WEDNESDAY),
                        startTime = JavaLocalTime.of(19, 0),
                        endTime = JavaLocalTime.of(23, 0),
                    ),
                ),
            ),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertNull(request)
    }

    @Test
    fun duplicateBlockSuppressesBlockRequest() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep",
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf("com.uiery.keep"),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = true,
        )

        assertNull(request)
    }

    @Test
    fun emergencyUnlockSuppressesBlockRequestForSelectedPackage() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep",
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf("com.uiery.keep"),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = true,
            isDuplicateBlock = false,
        )

        assertNull(request)
    }

    @Test
    fun serviceConnectionForegroundReevaluationUsesCurrentForegroundPackage() {
        val request = resolveServiceConnectionForegroundBlockRequest(
            currentForegroundPackage = "com.uiery.keep",
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf("com.uiery.keep"),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = "com.uiery.keep",
                blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            ),
            request,
        )
    }

    @Test
    fun serviceConnectionForegroundReevaluationSkipsWhenForegroundPackageIsUnavailable() {
        val request = resolveServiceConnectionForegroundBlockRequest(
            currentForegroundPackage = null,
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf("com.uiery.keep"),
            ),
            cachedRoutines = emptyList(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertNull(request)
    }

    @Test
    fun unselectedPackageWithoutActiveRoutineDoesNotBlock() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep.other",
            prefs = AccessibilityBlockingPreferences(
                isKeep = true,
                selectedAppPackages = setOf("com.uiery.keep"),
            ),
            cachedRoutines = listOf(activeRoutine(targetPackage = "com.uiery.keep.target")),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertNull(request)
    }

    @Test
    fun activeParentModeBlocksDisallowedPackageWithParentModeSource() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.game.app",
            prefs = AccessibilityBlockingPreferences(),
            cachedRoutines = emptyList(),
            parentModeSession = activeParentModeSession(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = "com.game.app",
                blockSource = AnalyticsBlockSource.PARENT_MODE,
            ),
            request,
        )
    }

    @Test
    fun parentModeKeepsParentControlPackageAccessibleForPinUnlock() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep",
            prefs = AccessibilityBlockingPreferences(),
            cachedRoutines = emptyList(),
            parentModeSession = activeParentModeSession(),
            parentControlPackages = setOf("com.uiery.keep"),
            now = LocalDateTime.of(2026, 5, 27, 10, 2),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertNull(request)
    }

    @Test
    fun parentModeAllowsExplicitPackageBeforeExpiryButBlocksItAfterExpiry() {
        val activeRequest = resolveForegroundBlockRequest(
            packageName = "com.video.app",
            prefs = AccessibilityBlockingPreferences(),
            cachedRoutines = emptyList(),
            parentModeSession = activeParentModeSession(),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )
        val expiredRequest = resolveForegroundBlockRequest(
            packageName = "com.video.app",
            prefs = AccessibilityBlockingPreferences(),
            cachedRoutines = emptyList(),
            parentModeSession = activeParentModeSession(),
            now = LocalDateTime.of(2026, 5, 27, 10, 2),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertNull(activeRequest)
        assertEquals(
            ForegroundBlockRequest(
                packageName = "com.video.app",
                blockSource = AnalyticsBlockSource.PARENT_MODE,
            ),
            expiredRequest,
        )
    }

    private fun activeParentModeSession(
        allowedApps: Set<String> = setOf("com.video.app"),
    ): ParentModeSession = ParentModeSession(
        startedAtMillis = LocalDateTime.of(2026, 5, 27, 10, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        expiresAtMillis = LocalDateTime.of(2026, 5, 27, 10, 1)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        durationMinutes = 1,
        allowedApps = allowedApps,
        state = ParentModeSessionState.Active,
    )

    private fun activeRoutine(
        id: Long = 1L,
        targetPackage: String,
    ): RoutineModel = RoutineModel(
        id = id,
        name = "Morning block",
        startTime = LocalTime(9, 0),
        endTime = LocalTime(11, 0),
        repeatDays = repeatDayBinary(DayOfWeek.WEDNESDAY),
        lockApplications = listOf(targetPackage),
        isEnabled = true,
    )

    private fun activeGoalLock(
        id: Long = 1L,
        targetPackage: String,
        mode: GoalLockMode,
    ): GoalLock = GoalLock(
        id = id,
        goalName = "시험 준비",
        startDate = LocalDate.of(2026, 5, 1),
        endDate = LocalDate.of(2026, 5, 31),
        lockMode = mode,
        selectedPackages = setOf(targetPackage),
        status = GoalLockStoredStatus.Active,
    )

    private fun repeatDayBinary(dayOfWeek: DayOfWeek): String = buildString {
        DayOfWeek.entries.forEach { day ->
            append(if (day == dayOfWeek) '1' else '0')
        }
    }

    private companion object {
        const val HOME_LAUNCHER = "com.sec.android.app.launcher"
        const val SETTINGS = "com.android.settings"
        const val DIALER = "com.samsung.android.dialer"
        const val INSTAGRAM = "com.instagram.android"
    }
}

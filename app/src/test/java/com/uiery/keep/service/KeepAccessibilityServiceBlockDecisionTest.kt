package com.uiery.keep.service

import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import com.uiery.keep.feature.parentmode.ParentModeSession
import com.uiery.keep.feature.parentmode.ParentModeSessionState
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

    private fun activeParentModeSession(): ParentModeSession = ParentModeSession(
        startedAtMillis = LocalDateTime.of(2026, 5, 27, 10, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        expiresAtMillis = LocalDateTime.of(2026, 5, 27, 10, 1)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        durationMinutes = 1,
        allowedApps = setOf("com.video.app"),
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
}

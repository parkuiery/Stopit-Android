package com.uiery.keep.service

import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.model.RoutineModel
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

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
    fun activeRoutineTargetUsesRoutineSourceWithoutManualSelection() {
        val request = resolveForegroundBlockRequest(
            packageName = "com.uiery.keep.target",
            prefs = AccessibilityBlockingPreferences(),
            cachedRoutines = listOf(activeRoutine(targetPackage = "com.uiery.keep.target")),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        )

        assertEquals(
            ForegroundBlockRequest(
                packageName = "com.uiery.keep.target",
                blockSource = AnalyticsBlockSource.ROUTINE,
            ),
            request,
        )
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

    private fun activeRoutine(targetPackage: String): RoutineModel = RoutineModel(
        id = 1L,
        name = "Morning block",
        startTime = LocalTime(9, 0),
        endTime = LocalTime(11, 0),
        repeatDays = repeatDayBinary(DayOfWeek.WEDNESDAY),
        lockApplications = listOf(targetPackage),
        isEnabled = true,
    )

    private fun repeatDayBinary(dayOfWeek: DayOfWeek): String = buildString {
        DayOfWeek.entries.forEach { day ->
            append(if (day == dayOfWeek) '1' else '0')
        }
    }
}

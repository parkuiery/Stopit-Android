package com.uiery.keep.datastore

import androidx.datastore.preferences.core.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreDataStoreKeyPolicyTest {

    @Test
    fun everyPreferencesKeyHasABackupRestoreClassification() {
        val declaredKeys = PreferencesKey.declaredPreferenceKeys()

        assertEquals(
            declaredKeys.map { it.name }.sorted(),
            BackupRestoreDataStoreKeyPolicy.classifiedKeys.map { it.name }.sorted(),
        )
    }

    @Test
    fun routinesIsTheOnlyRoomRehydratedCompatibilityCache() {
        assertEquals(
            setOf(PreferencesKey.ROUTINES),
            BackupRestoreDataStoreKeyPolicy.rehydratedCompatibilityCacheKeys,
        )
        assertTrue(PreferencesKey.ROUTINES !in BackupRestoreDataStoreKeyPolicy.resetOnlyKeys)
    }

    @Test
    fun resetOnlyKeysIncludeRuntimeDeviceReviewAnalyticsAndNoticeState() {
        val expectedResetOnlyKeys = setOf(
            PreferencesKey.SELECTED_APP_PACKAGES,
            PreferencesKey.SELECTED_WEB_DOMAINS,
            PreferencesKey.IS_KEEP,
            PreferencesKey.FCM_TOKEN,
            PreferencesKey.START_TIME,
            PreferencesKey.LOCK_TIME,
            PreferencesKey.IS_NEW,
            PreferencesKey.TOTAL_BLOCK_TIME,
            PreferencesKey.LONG_BLOCK_TIME,
            PreferencesKey.HAS_SHOWN_ALARM_PERMISSION,
            PreferencesKey.PREVENT_UNINSTALL,
            PreferencesKey.EMERGENCY_UNLOCK_APPS,
            PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME,
            PreferencesKey.EMERGENCY_UNLOCK_ENABLED,
            PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT,
            PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS,
            PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED,
            PreferencesKey.EMERGENCY_UNLOCK_AUTO_RESET_ENABLED,
            PreferencesKey.EMERGENCY_UNLOCK_MANUAL_RESET_AT,
            PreferencesKey.EMERGENCY_UNLOCK_COUNTDOWN_ENABLED,
            PreferencesKey.EMERGENCY_UNLOCK_COUNTDOWN_SECONDS,
            PreferencesKey.HAS_TRACKED_FIRST_OPEN,
            PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED,
            PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE,
            PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SELECTED_APP_COUNT,
            PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_REASON,
            PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_DURATION_MINUTES,
            PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_REMAINING,
            PreferencesKey.FIRST_OPEN_TIMESTAMP,
            PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION,
            PreferencesKey.REVIEW_PENDING,
            PreferencesKey.LAST_REVIEW_PROMPT_AT_MS,
            PreferencesKey.SUCCESSFUL_SESSION_COUNT,
            PreferencesKey.LAST_BACKGROUNDED_AT_MS,
            PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE,
            PreferencesKey.REPEAT_BLOCK_DISMISSED_SUGGESTIONS,
            PreferencesKey.PARENT_MODE_STARTED_AT,
            PreferencesKey.PARENT_MODE_EXPIRES_AT,
            PreferencesKey.PARENT_MODE_DURATION_MINUTES,
            PreferencesKey.PARENT_MODE_ALLOWED_APPS,
            PreferencesKey.PARENT_MODE_STATE,
            PreferencesKey.PARENT_MODE_PIN_HASH,
            PreferencesKey.PARENT_MODE_PIN_SALT,
            PreferencesKey.HAS_CHECKED_INSTALL_REFERRER_ATTRIBUTION,
            PreferencesKey.USAGE_INSIGHT_DISMISSED,
            PreferencesKey.USAGE_INSIGHT_PERMISSION_PROMPTS,
            PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE,
            PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN,
            PreferencesKey.FIRST_PROMISE_PRACTICE_DECISION,
            PreferencesKey.FIRST_PROMISE_CREATION_BARRIER_DRAFT_IDS,
            // 진행 중이던 집중 세션은 기기에 묶인 런타임 상태다. 복원된 기기에서 되살아나면
            // 사용자가 예약한 적 없는 잠금이 켜진 채로 앱이 열린다.
            PreferencesKey.POMODORO_PRESET,
            PreferencesKey.POMODORO_FOCUS_MINUTES,
            PreferencesKey.POMODORO_SHORT_BREAK_MINUTES,
            PreferencesKey.POMODORO_LONG_BREAK_MINUTES,
            PreferencesKey.POMODORO_CYCLES,
            PreferencesKey.POMODORO_STARTED_AT,
            PreferencesKey.POMODORO_PHASE,
            PreferencesKey.POMODORO_CYCLE_INDEX,
            PreferencesKey.POMODORO_PHASE_DEADLINE,
            PreferencesKey.POMODORO_COMPLETED_FOCUS_COUNT,
            PreferencesKey.POMODORO_STATUS,
            PreferencesKey.POMODORO_TODAY_DATE,
            PreferencesKey.POMODORO_TODAY_FOCUS_COUNT,
            PreferencesKey.POMODORO_BLOCK_DURING_BREAKS,
            PreferencesKey.POMODORO_LAST_PRESET,
            PreferencesKey.POMODORO_LAST_FOCUS_MINUTES,
            PreferencesKey.POMODORO_LAST_SHORT_BREAK_MINUTES,
            PreferencesKey.POMODORO_LAST_LONG_BREAK_MINUTES,
            PreferencesKey.POMODORO_LAST_CYCLES,
        )

        assertEquals(
            expectedResetOnlyKeys.map { it.name }.sorted(),
            BackupRestoreDataStoreKeyPolicy.resetOnlyKeys.map { it.name }.sorted(),
        )
    }

    private fun PreferencesKey.declaredPreferenceKeys(): Set<Preferences.Key<*>> =
        PreferencesKey::class.java.declaredFields
            .mapNotNull { field ->
                field.isAccessible = true
                field.get(this) as? Preferences.Key<*>
            }
            .toSet()
}

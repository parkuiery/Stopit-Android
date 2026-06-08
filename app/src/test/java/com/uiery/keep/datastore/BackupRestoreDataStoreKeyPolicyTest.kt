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
            PreferencesKey.HAS_TRACKED_FIRST_OPEN,
            PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED,
            PreferencesKey.FIRST_OPEN_TIMESTAMP,
            PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION,
            PreferencesKey.REVIEW_PENDING,
            PreferencesKey.LAST_REVIEW_PROMPT_AT_MS,
            PreferencesKey.SUCCESSFUL_SESSION_COUNT,
            PreferencesKey.LAST_BACKGROUNDED_AT_MS,
            PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE,
            PreferencesKey.REPEAT_BLOCK_DISMISSED_SUGGESTIONS,
            PreferencesKey.HAS_CHECKED_INSTALL_REFERRER_ATTRIBUTION,
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

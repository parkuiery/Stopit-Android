package com.uiery.keep.datastore

import androidx.datastore.preferences.core.Preferences

/**
 * Static backup/restore classification for every PreferencesKey entry.
 *
 * Stopit backs up Room DB only. The Preferences DataStore file is intentionally excluded,
 * so keys are either reset-only device/runtime state or a compatibility cache that can be
 * rebuilt from restored Room state after framework entry points run.
 */
object BackupRestoreDataStoreKeyPolicy {
    val resetOnlyKeys: Set<Preferences.Key<*>> = setOf(
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
        PreferencesKey.PARENT_MODE_STARTED_AT,
        PreferencesKey.PARENT_MODE_EXPIRES_AT,
        PreferencesKey.PARENT_MODE_DURATION_MINUTES,
        PreferencesKey.PARENT_MODE_ALLOWED_APPS,
        PreferencesKey.PARENT_MODE_STATE,
    )

    val rehydratedCompatibilityCacheKeys: Set<Preferences.Key<*>> = setOf(
        PreferencesKey.ROUTINES,
    )

    val classifiedKeys: Set<Preferences.Key<*>> = resetOnlyKeys + rehydratedCompatibilityCacheKeys
}

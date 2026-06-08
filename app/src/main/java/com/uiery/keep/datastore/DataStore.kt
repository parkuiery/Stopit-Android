package com.uiery.keep.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "keep-datastore")

object PreferencesKey {
    val SELECTED_APP_PACKAGES = stringSetPreferencesKey("selected_app_packages")
    val IS_KEEP = booleanPreferencesKey("is_keep")
    val FCM_TOKEN = stringPreferencesKey("fcm_token")
    val START_TIME = longPreferencesKey("start_time")
    val LOCK_TIME = stringPreferencesKey("lock_time")
    val IS_NEW = booleanPreferencesKey("is_new")
    val TOTAL_BLOCK_TIME = longPreferencesKey("total_block_time")
    val LONG_BLOCK_TIME = longPreferencesKey("long_block_time")
    val ROUTINES = stringPreferencesKey("routines")
    val HAS_SHOWN_ALARM_PERMISSION = booleanPreferencesKey("has_shown_alarm_permission")
    val PREVENT_UNINSTALL = booleanPreferencesKey("prevent_uninstall")
    val EMERGENCY_UNLOCK_APPS = stringSetPreferencesKey("emergency_unlock_apps")
    val EMERGENCY_UNLOCK_EXPIRE_TIME = longPreferencesKey("emergency_unlock_expire_time")
    val EMERGENCY_UNLOCK_ENABLED = booleanPreferencesKey("emergency_unlock_enabled")
    val EMERGENCY_UNLOCK_DAILY_LIMIT = intPreferencesKey("emergency_unlock_daily_limit")
    val EMERGENCY_UNLOCK_DURATION_OPTIONS = stringSetPreferencesKey("emergency_unlock_duration_options")
    val EMERGENCY_UNLOCK_REASON_REQUIRED = booleanPreferencesKey("emergency_unlock_reason_required")
    val EMERGENCY_UNLOCK_AUTO_RESET_ENABLED = booleanPreferencesKey("emergency_unlock_auto_reset_enabled")
    val EMERGENCY_UNLOCK_MANUAL_RESET_AT = longPreferencesKey("emergency_unlock_manual_reset_at")
    val HAS_TRACKED_FIRST_OPEN = booleanPreferencesKey("has_tracked_first_open")
    val HAS_TRACKED_FIRST_LOCK_CONFIGURED = booleanPreferencesKey("has_tracked_first_lock_configured")
    val FIRST_OPEN_TIMESTAMP = longPreferencesKey("first_open_timestamp")
    val HAS_TRACKED_FIRST_CORE_ACTION = booleanPreferencesKey("has_tracked_first_core_action")
    val REVIEW_PENDING = booleanPreferencesKey("review_pending")
    val LAST_REVIEW_PROMPT_AT_MS = longPreferencesKey("last_review_prompt_at_ms")
    val SUCCESSFUL_SESSION_COUNT = intPreferencesKey("successful_session_count")
    val LAST_BACKGROUNDED_AT_MS = longPreferencesKey("last_backgrounded_at_ms")
    val PENDING_ROUTINE_START_NOTICE_MESSAGE = stringPreferencesKey("pending_routine_start_notice_message")
    val REPEAT_BLOCK_DISMISSED_SUGGESTIONS = stringSetPreferencesKey("repeat_block_dismissed_suggestions")
    val HAS_CHECKED_INSTALL_REFERRER_ATTRIBUTION = booleanPreferencesKey("has_checked_install_referrer_attribution")
}

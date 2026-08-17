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
    val SELECTED_WEB_DOMAINS = stringSetPreferencesKey("selected_web_domains")
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
    val EMERGENCY_UNLOCK_COUNTDOWN_ENABLED = booleanPreferencesKey("emergency_unlock_countdown_enabled")
    val EMERGENCY_UNLOCK_COUNTDOWN_SECONDS = intPreferencesKey("emergency_unlock_countdown_seconds")

    // emergency_unlock_completed 는 해제 승인이 아니라 해제 창이 실제로 끝났을 때 기록한다.
    // 종료를 감지하는 쪽(AccessibilityService)은 승인 시점의 payload 를 모르고, 프로세스는
    // 창이 열려 있는 동안 죽을 수 있다. 그래서 승인 시점에 payload 를 예약해두고 종료 시점에
    // 꺼내 보낸다. reason 은 enum key 만 저장하며 custom reason 원문은 저장하지 않는다. (#1167)
    val PENDING_EMERGENCY_UNLOCK_COMPLETION_REASON =
        stringPreferencesKey("pending_emergency_unlock_completion_reason")
    val PENDING_EMERGENCY_UNLOCK_COMPLETION_DURATION_MINUTES =
        intPreferencesKey("pending_emergency_unlock_completion_duration_minutes")
    val PENDING_EMERGENCY_UNLOCK_COMPLETION_REMAINING =
        intPreferencesKey("pending_emergency_unlock_completion_remaining")
    val HAS_TRACKED_FIRST_OPEN = booleanPreferencesKey("has_tracked_first_open")
    val HAS_TRACKED_FIRST_LOCK_CONFIGURED = booleanPreferencesKey("has_tracked_first_lock_configured")
    val PENDING_FIRST_LOCK_CONFIGURED_SOURCE = stringPreferencesKey("pending_first_lock_configured_source")
    val PENDING_FIRST_LOCK_CONFIGURED_SELECTED_APP_COUNT =
        intPreferencesKey("pending_first_lock_configured_selected_app_count")
    val FIRST_OPEN_TIMESTAMP = longPreferencesKey("first_open_timestamp")
    val HAS_TRACKED_FIRST_CORE_ACTION = booleanPreferencesKey("has_tracked_first_core_action")
    val REVIEW_PENDING = booleanPreferencesKey("review_pending")
    val LAST_REVIEW_PROMPT_AT_MS = longPreferencesKey("last_review_prompt_at_ms")
    val SUCCESSFUL_SESSION_COUNT = intPreferencesKey("successful_session_count")
    val LAST_BACKGROUNDED_AT_MS = longPreferencesKey("last_backgrounded_at_ms")
    val PENDING_ROUTINE_START_NOTICE_MESSAGE = stringPreferencesKey("pending_routine_start_notice_message")
    val REPEAT_BLOCK_DISMISSED_SUGGESTIONS = stringSetPreferencesKey("repeat_block_dismissed_suggestions")
    val PARENT_MODE_STARTED_AT = longPreferencesKey("parent_mode_started_at")
    val PARENT_MODE_EXPIRES_AT = longPreferencesKey("parent_mode_expires_at")
    val PARENT_MODE_DURATION_MINUTES = intPreferencesKey("parent_mode_duration_minutes")
    val PARENT_MODE_ALLOWED_APPS = stringSetPreferencesKey("parent_mode_allowed_apps")
    val PARENT_MODE_STATE = stringPreferencesKey("parent_mode_state")
    val HAS_CHECKED_INSTALL_REFERRER_ATTRIBUTION = booleanPreferencesKey("has_checked_install_referrer_attribution")
    val USAGE_INSIGHT_DISMISSED = stringPreferencesKey("usage_insight_dismissed")
    val USAGE_INSIGHT_PERMISSION_PROMPTS = intPreferencesKey("usage_insight_permission_prompts")
    val FIRST_PROMISE_ONBOARDING_STATE = stringPreferencesKey("first_promise_onboarding_state")
    val FIRST_PROMISE_PRACTICE_TOKEN = stringPreferencesKey("first_promise_practice_token")
    val FIRST_PROMISE_PRACTICE_DECISION = stringPreferencesKey("first_promise_practice_decision")
    val FIRST_PROMISE_CREATION_BARRIER_DRAFT_IDS =
        stringSetPreferencesKey("first_promise_creation_barrier_draft_ids")
}

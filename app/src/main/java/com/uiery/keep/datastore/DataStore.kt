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
    // 원문이 아니라 salt+hash 만 남는다. 부모 모드는 폰을 든 사람이 세션을 만든 사람이
    // 아닌 유일한 잠금이라, 다시 타이핑할 수 있는 값이 기기에 남으면 게이트가 무의미하다.
    val PARENT_MODE_PIN_HASH = stringPreferencesKey("parent_mode_pin_hash")
    val PARENT_MODE_PIN_SALT = stringPreferencesKey("parent_mode_pin_salt")
    val HAS_CHECKED_INSTALL_REFERRER_ATTRIBUTION = booleanPreferencesKey("has_checked_install_referrer_attribution")
    val USAGE_INSIGHT_DISMISSED = stringPreferencesKey("usage_insight_dismissed")
    val USAGE_INSIGHT_PERMISSION_PROMPTS = intPreferencesKey("usage_insight_permission_prompts")
    val FIRST_PROMISE_ONBOARDING_STATE = stringPreferencesKey("first_promise_onboarding_state")
    val FIRST_PROMISE_PRACTICE_TOKEN = stringPreferencesKey("first_promise_practice_token")
    val FIRST_PROMISE_PRACTICE_DECISION = stringPreferencesKey("first_promise_practice_decision")
    val FIRST_PROMISE_CREATION_BARRIER_DRAFT_IDS =
        stringSetPreferencesKey("first_promise_creation_barrier_draft_ids")

    // 뽀모도로 세션. deadline 은 `LOCK_TIME` 과 같은 이유로 ISO-8601 `Instant` 문자열이다 —
    // timezone 이 바뀌어도 같은 실제 시각에 만료되어야 하고, 앱이 죽어 있던 동안 지나간 구간을
    // 저장된 값만으로 따라잡을 수 있어야 한다.
    val POMODORO_PRESET = stringPreferencesKey("pomodoro_preset")
    // 길이는 프리셋 key 가 아니라 분 값으로 남긴다. 커스텀 길이는 표에 없고, 프리셋
    // 정의가 바뀌어도 이미 돌고 있던 세션의 남은 시간이 소급해 달라지면 안 된다.
    val POMODORO_FOCUS_MINUTES = intPreferencesKey("pomodoro_focus_minutes")
    val POMODORO_SHORT_BREAK_MINUTES = intPreferencesKey("pomodoro_short_break_minutes")
    val POMODORO_LONG_BREAK_MINUTES = intPreferencesKey("pomodoro_long_break_minutes")
    val POMODORO_CYCLES = intPreferencesKey("pomodoro_cycles")
    val POMODORO_STARTED_AT = stringPreferencesKey("pomodoro_started_at")
    val POMODORO_PHASE = stringPreferencesKey("pomodoro_phase")
    val POMODORO_CYCLE_INDEX = intPreferencesKey("pomodoro_cycle_index")
    val POMODORO_PHASE_DEADLINE = stringPreferencesKey("pomodoro_phase_deadline")
    val POMODORO_COMPLETED_FOCUS_COUNT = intPreferencesKey("pomodoro_completed_focus_count")
    val POMODORO_STATUS = stringPreferencesKey("pomodoro_status")
    // "오늘 N번 집중했어요"는 세션 하나가 아니라 하루를 센다. 날짜가 바뀌면 0부터 다시 센다.
    val POMODORO_TODAY_DATE = stringPreferencesKey("pomodoro_today_date")
    val POMODORO_TODAY_FOCUS_COUNT = intPreferencesKey("pomodoro_today_focus_count")
    // 마지막으로 고른 사이클. 다시 쓸 때 고르는 과정을 통째로 건너뛰기 위한 값이라
    // 진행 중 세션과 별개로 남는다. 세션이 끝나도 지우지 않는다.
    // 휴식 중에도 막을지. 뽀모도로 카테고리 기본은 "휴식엔 풀림"이지만, 이 앱을 고른 이유가
    // 차단이므로 기본값은 계속 막는 쪽이다. `docs/POMODORO_FOCUS_MVP.md` 참고.
    val POMODORO_BLOCK_DURING_BREAKS = booleanPreferencesKey("pomodoro_block_during_breaks")
    val POMODORO_LAST_PRESET = stringPreferencesKey("pomodoro_last_preset")
    val POMODORO_LAST_FOCUS_MINUTES = intPreferencesKey("pomodoro_last_focus_minutes")
    val POMODORO_LAST_SHORT_BREAK_MINUTES = intPreferencesKey("pomodoro_last_short_break_minutes")
    val POMODORO_LAST_LONG_BREAK_MINUTES = intPreferencesKey("pomodoro_last_long_break_minutes")
    val POMODORO_LAST_CYCLES = intPreferencesKey("pomodoro_last_cycles")
}

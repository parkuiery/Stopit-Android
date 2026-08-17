# Amplitude Event Schema (Keep / StopIt)

Amplitude runs **alongside** Google Analytics (Firebase), not as a replacement. Firebase
keeps receiving the **full** event catalog; Amplitude receives a **curated allowlist** of
high‑signal lifecycle/funnel events so the project stays inside the **free tier**.

실데이터를 GA4와 함께 읽어 판독하는 절차는 `docs/analytics/GA4_AMPLITUDE_JOINT_ANALYSIS.md`
(`scripts/metrics_read.py`, Amplitude MCP)를 source of truth로 본다.

- Package: `com.uiery.keep`
- SDK: `com.amplitude:analytics-android` (Amplitude Kotlin Android SDK)
- Composition point: `analytics/FirebaseModule.kt` → `CompositeAnalyticsBackend([Firebase, Amplitude])`
- Allowlist source of truth: `analytics/amplitude/AmplitudeEventAllowlist.kt`

---

## 1. Free-tier budget

| Limit (Starter/Free plan) | Value |
|---|---|
| Monthly Tracked Users (MTU) | 10,000 / mo |
| Events | 2,000,000 / mo |
| Effective per-user ceiling | 2,000,000 ÷ 10,000 ≈ **~200 events / user / mo (average)** |

The binding constraint is the **2M events/mo** cap, not MTU. Three layered controls keep us
under it — the third is a **hard guarantee**, not just a best effort:

1. **Event allowlist** — only ~22 event types reach Amplitude (below).
2. **Autocapture fully OFF** — sessions, screen views, deep links, element interactions and
   app-lifecycle autocapture are all disabled. This is deliberate: autocapture events are
   emitted *inside* the SDK and would bypass our allowlist and budget gates. With autocapture
   off, **every** Amplitude event flows through our controls.
3. **Hard per-device monthly cap** — `BudgetCappedBackend` + `AmplitudeEventBudget` count
   forwarded events per device per calendar month and **drop** everything past
   `AMPLITUDE_MONTHLY_EVENT_CAP` (default **180**). Guarantee:
   `cap × maxMTU = 180 × 10,000 = 1,800,000 < 2,000,000`. Total ingestion can never reach the
   billing threshold, regardless of how heavily any individual user behaves.

### Tuning the cap to "use the free tier fully, but never exceed it"

The cap is a `buildConfigField` (`AMPLITUDE_MONTHLY_EVENT_CAP` in `app/build.gradle.kts`). Set
it as high as `floor(2,000,000 / expected_max_MTU)` to maximize free-tier usage:

| Expected max MTU | Safe cap (events/device/mo) |
|---|---|
| 10,000 (free ceiling) | 180 |
| 5,000 | 380 |
| 2,000 | 950 |
| 1,000 | 1,800 |

### Budget estimate (per active user / month, autocapture off)

| Bucket | Events (typical) | Events (heavy) |
|---|---|---|
| `app_first_open` + onboarding funnel | ~6 (once) | ~6 |
| `lock_session_start` + `lock_session_end` | ~40 | ~120 |
| Emergency unlock / goal lock / routine / parent mode | ~10 | ~30 |
| Retention surfaces / monetization intent | ~5 | ~15 |
| **Total** | **~60** | **~170** |

Even a heavy user stays under the 180 default cap; anyone who somehow exceeds it simply has
further events dropped for that month. The volume-dominant pair is `lock_session_start` +
`lock_session_end` — the first candidate to sample if you lower the cap aggressively.

---

## 2. Events sent to Amplitude (allowlist)

All parameters are already **privacy-bucketed** by the app's analytics layer: no raw package
names (category buckets instead), no raw routine/goal IDs, durations/counts bucketed. Amplitude
receives exactly what Firebase receives for these events.

| Event | When | Key properties |
|---|---|---|
| `app_first_open` | First launch (custom; Firebase uses reserved `first_open`) | — |
| `onboarding_step_complete` | Onboarding step finished | `step_name` |
| `permission_outcome` | Accessibility/notification permission resolved | `permission_name`, `outcome`, `step_name?` |
| `app_selection_completed` | App selection confirmed | `selected_app_count`, `is_onboarding` |
| `first_lock_configured` | First lock configured | `source`, `selected_app_count?` |
| `first_core_action_completed` | First real block delivered (activation) | common: `blocking_mode`, `blocked_app_category_bucket`; ordinary direct path: `elapsed_since_first_open_seconds`; durable first-promise path: `elapsed_since_first_open_bucket`, `promise_origin` |
| `keep_mode_toggled` | Keep switch on/off | `is_enabled` |
| `lock_session_start` | Lock session begins | `source`, `is_routine?` |
| `lock_session_end` | Lock session ends | `source`, `end_reason`, `is_routine?` |
| `lock_scheduled` | Timer/countdown/routine lock scheduled | `schedule_type`, `scheduled_duration_minutes` |
| `emergency_unlock_used` | Emergency unlock consumed | `source`, `unlock_count_remaining?` |
| `emergency_unlock_completed` | ⚠️ **승인 시점**에 발생 (아래 주석 참조) | `reason`, `duration_minutes`, `remaining_unlocks` |
| `goal_lock_created` | Goal lock created | `duration_selection_type`, `lock_mode`, `selected_app_count_bucket`, `goal_name_type` |
| `goal_lock_completed` | Goal lock finished | `lock_mode`, `duration_days_bucket` |
| `goal_lock_ended_early` | Goal lock ended early | `lock_mode`, `elapsed_days_bucket`, `reason` |
| `routine_saved` | Routine created/updated | `entry_surface`, `creation_source`, `selected_app_count_bucket`, `repeat_days_bucket`, `time_window_bucket`, `schedule_state` |
| `routine_creation_cta_clicked` | Routine-creation CTA clicked | `surface`, `activation_stage`, `has_routine`, `cta_variant?` |
| `parent_mode_started` | Parent mode started | `duration_minutes_bucket`, `allowed_app_count_bucket` |
| `parent_mode_completed` | Parent mode ended | `duration_minutes_bucket`, `end_reason` |
| `lock_history_performance_summary_viewed` | Performance summary viewed | `period_type`, `report_state`, `session_count_bucket`, `duration_minutes_bucket` |
| `focus_summary_share_tapped` | Focus summary share tapped | `period_type`, `session_count_bucket`, `duration_minutes_bucket` |
| `review_prompt_shown` | In-app review prompt shown | — |
| `monetization_interest_clicked` | Monetization interest clicked | `interest_surface`, `interest_context`, `interest_variant?`, `purchase_available?` |

> ⚠️ `emergency_unlock_completed`는 이름과 달리 해제 창 종료가 아니라 **해제 승인 시점**에
> 발생한다. `EmergencyUnlockCoordinator.kt:119-127`이 `emergency_unlock_used`와 조건 없이
> 연달아 호출하므로 두 이벤트는 항상 동일한 수치가 된다. 완료율 지표로 쓰면 승인율을 재게
> 되고, 고빈도 이벤트라 기기당 월 캡도 이중 소모한다. 상세와 처리 방향은
> `docs/analytics/GA4_AMPLITUDE_JOINT_ANALYSIS.md` §6 참조.

`first_core_action_completed` is allowlisted by event name, so both path-specific payload
shapes above reach Amplitude exactly as Firebase receives them. The ordinary direct path may
retain exact `elapsed_since_first_open_seconds` for canonical compatibility. The durable
first-promise sequence-40 path never stores or sends exact seconds; it sends only
`elapsed_since_first_open_bucket=under_1m|1_5m|over_5m` plus typed
`promise_origin=first_promise_routine|first_promise_practice`, with the common
`blocking_mode` and `blocked_app_category_bucket` properties. The two elapsed keys are never
present in the same payload.

---

## 3. Events NOT sent to Amplitude (Firebase-only)

Excluded to protect the budget and/or because they carry low marginal value in Amplitude:

- **First-promise experiment (all six new events):** `onboarding_experiment_exposed`,
  `usage_analysis_completed`, `promise_recommendation_shown`,
  `promise_recommendation_edited`, `first_promise_created`,
  `first_promise_practice_outcome`. Phase 1's ordered promise-value funnel stays in
  Firebase/GA4. These names are deliberately absent from `AmplitudeEventAllowlist`, and
  the allowlist regression asserts both that exclusion and the `<=30` event ceiling.
- **First-promise value attribution:** optional typed
  `app_block_intercepted.promise_origin=first_promise_routine|first_promise_practice`
  remains Firebase/GA4-only with the canonical sequence-30 `app_block_intercepted` event.
  Its paired sequence-40 `first_core_action_completed` is allowlisted and therefore reaches
  both Firebase and Amplitude with the durable bucket schema documented above. No package,
  app label, observed usage minutes/time, exact elapsed seconds, draft id, routine id, or
  arbitrary origin string is forwarded by the durable first-promise path.
- **High-frequency:** `app_block_intercepted`, `core_action_completed`,
  `parent_mode_block_intercepted`
- **Screen views:** `logScreenView` / `screen_view` (Amplitude has its own session model)
- **Granular funnel/noise:** `onboarding_step_view`, `emergency_unlock_step_viewed`,
  `emergency_unlock_validation_blocked`, `emergency_unlock_cancelled`,
  `emergency_unlock_settings_changed`, `emergency_unlock_manual_reset_requested`,
  `parent_mode_duration_selected`, `parent_mode_allowed_apps_selected`,
  `parent_mode_unlocked_by_pin`, `parent_mode_extended`, `parent_mode_cancelled`,
  `goal_lock_create_started`, `goal_lock_updated`, `routine_creation_cta_shown`,
  `routine_creation_cta_dismissed`, `monetization_interest_shown`,
  `focus_summary_share_sheet_opened`, `focus_summary_share_failed`,
  `lock_history_top_apps_viewed`, `support_contact_*`, `routine_template_share_*`,
  `repeat_block_routine_suggestion_*`
- **Infra:** `fcm_token_captured`, `device_registration_*`, `install_referrer_attribution_checked`
- **Ads:** `ad_*` banner events (these also flow through the composite via `TrackedBannerAd`'s
  Hilt `@EntryPoint`, and are dropped by the allowlist)

---

## 4. User properties & PII policy

- User properties are always forwarded to Amplitude (they carry no per-event cost).
- **No PII** is ever sent: no email, no raw package names, no free-text reasons, no row IDs.
  The app's analytics layer already enforces privacy-safe buckets; Amplitude inherits the same
  guarantees because it consumes the identical `params` map.

---

## 5. Autocapture policy & hard budget cap

Configured in `AmplitudeAnalyticsBackendFactory`:

```
autocapture = emptySet()   // ALL autocapture OFF (sessions, screen views, deep links,
                           // element interactions, app lifecycles)
```

Autocapture is fully off so no SDK-internal event can bypass our gates. Every Amplitude event
passes through, in order:

```
AllowlistFilteringBackend( BudgetCappedBackend( AmplitudeAnalyticsBackend ) )
        │                          │
        │                          └─ AmplitudeEventBudget: per-device SharedPreferences
        │                             counter, calendar-month reset, drops past the cap
        └─ only allowlisted event names are forwarded
```

- `AMPLITUDE_MONTHLY_EVENT_CAP` (BuildConfig `int`, default 180) is the hard ceiling.
- `EventBudgetPolicy` holds the pure cap/reset logic (unit-tested in `EventBudgetPolicyTest`).
- Because sessions are off, retention/stickiness is derived from explicit lifecycle events
  (`app_first_open`, `lock_session_start/end`, …) rather than Amplitude's auto session grouping.
  This is the intended trade for a hard, never-exceed guarantee.

---

## 6. Setup (Amplitude project + API key)

1. Create a **prod** Amplitude project and copy its **API Key**. A separate **dev** project
   is **optional** — the `dev` flavor ships with `AMPLITUDE_ENABLED = false`, a hard guard that
   makes dev a no-op regardless of any key, so debug traffic never reaches Amplitude and never
   pollutes production data. Only create/enable a dev project if you actually want to test the
   Amplitude pipeline in debug (set `AMPLITUDE_ENABLED = true` for dev and add `amplitude.apiKey.dev`).
2. Provide the key to the build. A **single** key is used; the dev flavor is gated off by
   `AMPLITUDE_ENABLED`, not by a separate key. Resolution order (`app/build.gradle.kts`):
   1. Env var — `AMPLITUDE_API_KEY` (CI)
   2. `local.properties` — `AMPLITUDE_API_KEY=...` (local dev)
   3. Empty string → Amplitude backend becomes **NoOp** (keyless builds still compile & run)

   `local.properties` example (do **not** commit):
   ```
   AMPLITUDE_API_KEY=YOUR_KEY
   ```

   This is the **write-only client ingestion key**. It cannot read data back — analysis
   goes through the Amplitude MCP server or the Dashboard REST API key/secret pair. See
   `docs/analytics/GA4_AMPLITUDE_JOINT_ANALYSIS.md`.
3. Build & run. Verify events land in the Amplitude dev project (User Look-Up / live stream).
   Without a key, the app runs normally and simply sends nothing to Amplitude.

---

## 7. Adding a new event to Amplitude

1. Ensure the event is emitted through the app's `KeepAnalytics` / `AnalyticsBackend` layer.
2. Add its name constant to `AmplitudeEventAllowlist.EVENTS`.
3. Add a row to the table in §2 with its properties.
4. `AmplitudeEventAllowlistTest` guards that every allowlisted name matches a real event
   constant and that the allowlist stays within the budgeted ceiling — run
   `./gradlew :app:testDevDebugUnitTest`.

> New events are **opt-out of Amplitude by default**: they keep flowing to Firebase but do not
> reach Amplitude until explicitly allowlisted. This is intentional budget protection.

# Emergency Unlock Settings Design

## Overview

Keep already supports emergency unlock from the lock screen. The current behavior is fixed: users can unlock selected blocked apps after choosing a reason, selecting a duration, and waiting through a 30 second countdown. This feature adds a user-facing settings screen for that policy while preserving the existing emergency-unlock history and enforcement model.

The goal is to let users tune the emergency-unlock policy before they are blocked, not while they are trying to bypass a block. Settings live under the menu screen, and the lock screen only consumes the saved policy.

## Goals

- Add a menu entry and dedicated settings screen for emergency unlock policy.
- Let users enable or disable emergency unlock.
- Let users choose a daily limit from 0 to 5 unlocks, with 3 as the default.
- Let users choose which duration presets are available on the lock screen, with 3, 5, and 10 minutes as the default selected presets.
- Let users enable or disable the reason step, with the reason step enabled by default.
- Keep the emergency unlock countdown fixed at 30 seconds.
- Preserve existing Room history and active unlock state behavior.

## Non-Goals

- Do not add a setting for the 30 second countdown.
- Do not change the `emergency_unlock` Room table.
- Do not add a history viewer for emergency unlock records.
- Do not allow emergency-unlock policy edits from the lock screen.
- Do not add a new dependency or design-system component.

## User Flow

The menu screen adds a new `Emergency unlock settings` item. Tapping it opens a dedicated settings screen with a top app bar and back navigation.

The settings screen contains:

- `Use emergency unlock`: on/off toggle. Default is on.
- `Daily unlock limit`: single choice from 0, 1, 2, 3, 4, and 5. Default is 3.
- `Available unlock durations`: multi-choice presets. Default selected presets are 3, 5, and 10 minutes.
- `Ask unlock reason`: on/off toggle. Default is on.

On the lock screen:

- If emergency unlock is disabled, the emergency unlock request is unavailable.
- If the daily limit is 0, the emergency unlock request is unavailable.
- If the daily limit has been reached, the existing limit-reached state remains.
- The duration step only shows the saved duration presets.
- If `Ask unlock reason` is off, the bottom sheet starts at app selection and skips reason selection.
- The 30 second countdown always remains in the flow after the request step.

## Duration Presets

The user-facing duration preset allowlist is fixed for this feature:

- 3 minutes
- 5 minutes
- 10 minutes
- 15 minutes

The default selected set is 3, 5, and 10 minutes to preserve the current lock-screen behavior after upgrade.

The settings UI must not allow users to save an empty duration list. If the user attempts to deselect the final selected duration, keep it selected and leave the saved setting unchanged.

## Data Model

Settings are user preferences, so they should be stored in the existing `keep-datastore`.

New preference keys:

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `EMERGENCY_UNLOCK_ENABLED` | Boolean | `true` | Whether emergency unlock can be requested. |
| `EMERGENCY_UNLOCK_DAILY_LIMIT` | Int | `3` | Maximum completed emergency unlocks per local day. |
| `EMERGENCY_UNLOCK_DURATION_OPTIONS` | StringSet | `3,5,10` | Duration presets shown in the bottom sheet. |
| `EMERGENCY_UNLOCK_REASON_REQUIRED` | Boolean | `true` | Whether the reason step appears before app selection. |

Because Preferences DataStore has no native `IntSet`, durations should be stored as a `StringSet` of minute values. Parsing and sorting should be centralized in policy code so UI and ViewModels consume a sanitized `List<Int>`.

## Policy Layer

`EmergencyUnlockPolicy.kt` should become the single place that sanitizes settings and calculates availability.

The current fixed constant `DAILY_EMERGENCY_UNLOCK_LIMIT = 3` should be replaced or wrapped by setting-aware functions such as:

- `emergencyUnlockDefaultSettings()`
- `sanitizeEmergencyUnlockDailyLimit(value)`
- `sanitizeEmergencyUnlockDurationOptions(values)`
- `isEmergencyUnlockAvailable(enabled, dailyLimit, todayUnlockCount)`
- `isEmergencyUnlockDailyLimitReached(dailyLimit, todayUnlockCount)`
- `emergencyUnlockDailyRemaining(dailyLimit, todayUnlockCount)`

Policy rules:

- Daily limit must be in `0..5`; invalid values fall back to 3.
- Duration options must be filtered to the fixed allowlist `3, 5, 10, 15`.
- Sanitized duration options must be sorted ascending.
- Duration options must be non-empty after filtering; invalid or empty persisted values fall back to 3, 5, and 10.
- A disabled policy is unavailable regardless of count.
- A daily limit of 0 is unavailable regardless of count.
- Existing active emergency unlocks are not modified when settings change. Settings apply to the next request.

## UI and Navigation

Add a new package:

`app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/`

Expected files:

- `EmergencyUnlockSettingsNavigation.kt`
- `EmergencyUnlockSettingsScreen.kt`
- `EmergencyUnlockSettingsViewModel.kt`

Navigation follows the existing feature pattern:

- Add `EmergencyUnlockSettingsRoute`.
- Add `NavController.navigateToEmergencyUnlockSettings()`.
- Add `NavGraphBuilder.emergencyUnlockSettingsScreen(...)`.
- Add the destination to `KeepApp`.
- Add a menu item in `MenuScreen` and pass an `onNavigateEmergencyUnlockSettings` callback through `MenuNavigation`.

The screen should reuse existing KDS and app patterns. Toggles should match the existing menu toggle style where practical, and choice controls should stay compact and readable.

## Request Surface Integration

Emergency unlock request behavior exists in two current surfaces:

- `feature/lock/LockScreen` and `LockViewModel`
- root `BlockScreen` and `BlockViewModel`

Both request surfaces must consume the same saved settings and policy functions. Neither surface should expose policy editing controls.

`LockViewModel` and `BlockViewModel` should read emergency unlock settings from DataStore during initialization and expose them through their UI state.

Suggested state additions:

- `emergencyUnlockEnabled: Boolean`
- `emergencyUnlockDailyLimit: Int`
- `emergencyUnlockDurationOptions: List<Int>`
- `emergencyUnlockReasonRequired: Boolean`

Each ViewModel's daily-limit check should calculate limit state using the saved daily limit rather than a fixed constant.

Each `emergencyUnlock(...)` implementation should re-check the current settings before creating an unlock. This prevents a stale bottom sheet from completing an unlock after the user changed settings elsewhere.

`EmergencyUnlockBottomSheetContent` should accept:

- `durationOptions: List<Int>`
- `reasonStepEnabled: Boolean`

If `reasonStepEnabled` is false, the first step is app selection. When the unlock is recorded, keep the existing `EmergencyUnlockEntity` schema and save `reason = "not_required"` and `customReason = null`.

## Enforcement

`KeepAccessibilityService` currently enforces active emergency unlocks through `EmergencyUnlockState` and DataStore-backed cached preferences. That model should remain unchanged.

The settings feature decides whether a new emergency unlock can be requested. Active unlock enforcement still depends only on:

- selected unlocked apps
- unlock expiry time

This keeps runtime blocking logic small and avoids adding settings reads to the accessibility-service hot path. The `BlockScreen` request path launched from the accessibility service still consumes settings through `BlockViewModel`, before creating a new active unlock.

## Error Handling

The system should prefer safe defaults when settings are missing or invalid.

- Missing `enabled` value defaults to `true`.
- Missing or invalid daily limit defaults to 3.
- Missing, empty, or invalid duration options default to 3, 5, and 10 minutes.
- Missing `reason required` value defaults to `true`.
- If emergency unlock is unavailable, the lock screen must not allow the request to complete.

## Analytics and History

Existing emergency unlock analytics and Room history should remain in place.

No schema change is required. When the reason step is disabled, save `reason = "not_required"` and `customReason = null`. This keeps analytics and history queries compatible with the current entity.

If implementation finds an existing analytics contract for policy settings, add events for settings changes. Otherwise, avoid adding analytics in this feature to keep scope narrow.

## Test Strategy

Extend `EmergencyUnlockPolicyTest` first because the highest-risk behavior is policy calculation.

Policy tests:

- Daily limit uses the configured value.
- Limit 0 makes emergency unlock unavailable.
- Disabled policy makes emergency unlock unavailable.
- Remaining count never drops below 0.
- Invalid daily limits fall back to 3.
- Empty or invalid duration options fall back to 3, 5, and 10.
- Valid duration options preserve sorted, allowed values.
- Existing active unlock package/expiry behavior still works.

ViewModel tests should be added if the existing test setup supports DataStore-backed ViewModels without heavy scaffolding:

- Settings screen writes enabled, daily limit, durations, and reason-step values.
- LockViewModel exposes sanitized settings.
- LockViewModel blocks unlock completion when settings make it unavailable.
- BlockViewModel exposes sanitized settings.
- BlockViewModel blocks unlock completion when settings make it unavailable.

Manual verification:

- Menu opens the emergency unlock settings screen.
- Settings persist after navigating away and back.
- Duration options selected in settings appear on the lock-screen bottom sheet.
- Duration options selected in settings appear on the blocked-app bottom sheet.
- Disabling the reason step starts the bottom sheet at app selection.
- Disabling emergency unlock or choosing 0 daily unlocks prevents emergency unlock from being requested.
- Existing 30 second countdown still runs before unlock completion.

## Open Implementation Notes

- The user-facing string keys should be added to all supported `values-*` string files or follow the repository's existing localization fallback policy.
- If the menu item needs an icon, reuse an existing lock/shield-style asset before adding a new vector.
- If the settings screen needs a reusable segmented choice component, prefer a small local composable inside the feature package unless another screen already has a suitable component.

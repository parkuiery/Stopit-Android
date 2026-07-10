# Goal Lock Detail and Edit Design

## Objective

Make the goal-lock detail screen a clear, read-only status surface that follows Keep's design system, and move all supported editing into a dedicated screen. Preserve the existing goal-lock data model, update rules, analytics, and termination behavior.

## Product Decisions

- The detail screen is for understanding the current goal and taking high-level actions.
- A separate edit screen owns changes to goal name, duration, lock mode, and selected apps.
- Editing is available only while the stored goal is active. Completed or ended-early goals remain read-only.
- All edits are saved together with one primary action. The current per-field confirmation cards are removed.
- Goal termination remains on the detail screen because it changes the goal lifecycle rather than its configuration.
- This work does not add goal history, multiple-goal management, new lock modes, or new persistence fields.

## Navigation

Add a type-safe `GoalLockEditRoute(goalLockId: Long)`.

- Active detail -> top-app-bar `수정` -> edit route.
- Edit save success -> navigate up to the existing detail entry.
- Edit back with no changes -> navigate up immediately.
- Edit back with unsaved changes -> show a discard confirmation dialog.
- Completed or ended-early detail -> no edit entry point.
- Missing goal on either screen -> navigate back through the existing not-found side effect pattern.

Returning from edit must refresh the detail content. The detail destination reloads the goal when it resumes or receives an explicit edit-complete result; the implementation plan may select the smallest approach compatible with the repository's Navigation Compose patterns.

## Detail Screen

### Layout hierarchy

1. Top app bar
   - Back navigation icon uses the existing neutral KDS color hierarchy.
   - Title uses the KDS title typography.
   - A text action `수정` appears only for editable goals.
2. Status hero card
   - Goal name is the primary heading.
   - A status badge distinguishes pending, active, completed, and ended-early states with text and shape, not color alone.
   - Supporting text shows the date range and a human-readable remaining/progress summary.
3. Goal information card
   - Read-only rows show duration/date range, lock mode, and selected-app count.
   - Rows use consistent labels, values, spacing, and KDS typography.
4. Selected apps section
   - Show the current app selection using the existing app-row presentation where practical.
   - If app labels cannot be resolved, retain the existing unknown-app fallback behavior.
5. Lifecycle action
   - `목표 잠금 종료` is visually separated from ordinary actions and uses the error/destructive hierarchy.
   - Confirmation is presented in a dialog rather than an inline card.
   - Completed and ended-early goals do not show this action.

### Design-system usage

- Use `KeepTheme` color and typography tokens instead of screen-local `sp`, raw colors, or arbitrary Material defaults.
- Reuse existing app setup components such as `SetupGroupCard`, section headers, and app rows when their semantics fit.
- Use KDS components such as `KeepButton` and `KeepModalBottomSheet` for their existing roles.
- Do not add a new shared KDS primitive for a component used only by goal lock. Feature-specific status badges or detail rows remain in the goal-lock feature.
- Maintain 20dp horizontal screen padding and the spacing rhythm already established by the goal-lock creation flow.

### Accessibility

- Preserve a concise combined summary semantics description for the hero content.
- Status must be announced as text.
- Icon-only actions require localized content descriptions.
- Destructive confirmation must expose clear confirm and cancel labels.
- Touch targets must meet the existing Compose/Material minimum sizing behavior.

## Edit Screen

### Layout

The edit screen mirrors the successful information architecture of goal-lock creation without showing creation presets that are not needed for editing.

1. Top app bar with back navigation and `목표 수정` title.
2. Goal name group using `SetupTextField`.
3. Duration group showing the date range, preset durations, stepper, and end-date picker.
4. Lock mode group using the same all-day and scheduled selection cards as creation.
5. Selected-app group using the existing category picker bottom sheet and removable app rows.
6. Full-width `변경사항 저장` primary button at the bottom.

The save button is enabled only when all input is valid and at least one field differs from the persisted goal. Saving is guarded against duplicate taps while persistence is in progress.

### Unsaved-change behavior

Dirty state is derived by comparing normalized editable values with the loaded goal. It is not maintained as an independent mutable flag.

- System back and top-app-bar back follow the same discard policy.
- The discard dialog offers `계속 수정` and `변경사항 버리기`.
- App-picker changes update the edit draft immediately but are not persisted until the screen-level save action.

## State and Ownership

### `GoalLockDetailViewModel`

Owns only:

- Loading and normalizing the requested goal.
- Exposing read-only status and display data.
- Completing an expired goal through the existing policy.
- Ending an active goal early and tracking that lifecycle event.

Per-field draft and update-confirmation state is removed from the detail state after the edit flow is established.

### `GoalLockEditViewModel`

Owns:

- Loading the goal identified by the route.
- A normalized edit draft for name, dates/duration, lock mode, and packages.
- Validation, dirty-state derivation, save-in-progress state, and persistence errors.
- A single repository update on save.
- Existing privacy-safe analytics for each field that actually changed.
- Save-complete and not-found side effects.

The ViewModel must reject editing if the persisted goal is no longer active at load or save time. Before saving, fetch the latest goal again or otherwise use the repository's established consistency pattern so a terminal goal is not overwritten by a stale draft.

### Repository and domain

No schema or repository API expansion is required unless implementation discovery proves the existing single `update(GoalLock)` operation cannot safely support the atomic screen-level save. The edit ViewModel constructs one updated `GoalLock` and persists it once.

Existing normalization and validity rules remain authoritative:

- Goal name is trimmed and must not be blank.
- At least one package must remain selected.
- Duration is at least one day and uses the existing start-date/end-date semantics.
- Scheduled lock mode must pass the existing update validation.
- An end date that makes the goal expired follows the current completion policy.

## Error Handling

- Initial loading uses an explicit loading state rather than displaying empty editable controls.
- Missing or no-longer-editable goals emit a side effect and leave the edit screen.
- Save failure keeps the draft on screen, clears the saving flag, and presents a localized retryable error through the app's established snackbar pattern.
- App picker cancellation keeps the existing draft selection unchanged.
- Goal-end confirmation cancellation is side-effect free.

## Analytics

- Retain the existing goal-detail screen view event.
- Add or reuse the established screen-view identity for the edit destination according to the analytics enum pattern.
- On a successful save, emit existing `goal_lock_updated` tracking once for each changed field, with no package names, goal names, or other user-entered values.
- Do not emit update analytics for unchanged fields, cancelled edits, validation failures, or failed persistence.
- Retain the existing early-end analytics behavior.

## Testing

### Unit tests

- Detail ViewModel loads active, completed, ended, and missing goals and exposes the correct available actions.
- Edit ViewModel initializes every draft field from the persisted goal.
- Dirty state is false initially and accurately reflects normalized changes and reversions.
- Validation covers blank name, empty app selection, invalid schedule, and duration bounds.
- Save performs one repository update with all draft values and tracks only changed fields.
- Save rejects a goal that became terminal after the edit screen loaded.
- Failed save retains the draft and exposes retryable failure state.
- Discard-confirmation policy is covered as a pure state/helper contract where practical.

### UI/navigation policy tests

- Edit navigation preserves the detail destination below it in the back stack.
- Successful save returns to detail.
- Terminal detail states do not expose edit or end actions.
- Detail and edit accessibility summary builders remain covered.

### Verification

- Run `:app:testDevDebugUnitTest`.
- Run `:app:lintDevDebug`.
- Run `:app:assembleProdDebug`.
- Perform device or preview inspection for light/dark theme, long Korean text, font scaling, scrolling, TalkBack labels, discard confirmation, destructive confirmation, and returning from edit to refreshed detail.

## Implementation Boundaries

- Keep goal-lock orchestration in `app`; no new dependency is introduced.
- Reuse creation-flow components before adding feature-local alternatives.
- Do not modify unrelated routine, home, or lock-screen UI.
- Preserve the current menu behavior: a current goal opens detail, and no current goal opens creation.
- Preserve the creation-to-detail back-stack fix: backing out of a newly created goal does not reveal the creation screen.

## Acceptance Criteria

- The detail screen presents goal status and configuration without editable fields or inline update confirmations.
- Active goals expose one clear edit entry point and one visually separated end action.
- The edit screen supports name, duration, lock mode, and selected-app changes with one save action.
- Unsaved changes are protected on back navigation.
- Saving returns to a refreshed detail screen.
- Completed and ended-early goals are read-only.
- UI uses the established KDS theme, typography, component, spacing, and accessibility conventions.
- Existing data, analytics privacy constraints, goal lifecycle rules, and navigation fixes remain intact.

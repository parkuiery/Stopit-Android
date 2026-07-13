# Goal Lock Detail and Edit Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace inline goal-lock editing with a KDS-aligned read-only detail screen and a dedicated atomic-save edit screen.

**Architecture:** Add pure status and edit policies first, then build the complete edit destination while the current inline editing remains available. Only after the edit route works do we atomically simplify detail to lifecycle ownership and connect its edit action. A Navigation saved-state result triggers detail reload after save.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Keep Design System, Navigation Compose, Hilt, Orbit MVI, Room repository, JUnit.

**Spec:** `docs/superpowers/specs/2026-07-10-goal-lock-detail-edit-design.md`

---

## Chunk 1: Policies and Complete Edit State

## File Map

| Action | File | Responsibility |
| --- | --- | --- |
| Modify | `KeepApp.kt`, menu files, `GoalLockDetailNavigation.kt` | Preserve already-built current-goal and creation back-stack fixes. |
| Create | `feature/goallock/GoalLockDetailPresentation.kt` | Pure runtime action and state-specific progress model. |
| Create | `feature/goallock/GoalLockEditViewModel.kt` | Load/retry, normalized draft, validation, discard policy, atomic save, analytics. |
| Create/Modify | Matching JVM tests | Freeze navigation, presentation, and edit persistence contracts. |

### Task 1: Preserve Existing Goal Entry Navigation

**Files:**
- Modify: `app/src/main/java/com/uiery/keep/KeepApp.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/menu/MenuNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/menu/MenuScreen.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/menu/MenuViewModel.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailNavigation.kt`
- Test: `app/src/test/java/com/uiery/keep/KeepAppNavigationPolicyTest.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/menu/MenuViewModelTest.kt`

- [ ] **Step 1: Inspect the existing diff**

Verify pending/active goals resolve to detail, terminal-only data resolves to creation, and creation-to-detail inclusively removes `GoalLockCreationRoute`.

- [ ] **Step 2: Run focused tests**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.menu.MenuViewModelTest" --tests "com.uiery.keep.KeepAppNavigationPolicyTest"
```

Expected: `BUILD SUCCESSFUL`. If not, fix only the failing prerequisite, rerun the same command, and require success.

- [ ] **Step 3: Commit only prerequisite files**

```bash
git add app/src/main/java/com/uiery/keep/KeepApp.kt app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailNavigation.kt app/src/main/java/com/uiery/keep/feature/menu/MenuNavigation.kt app/src/main/java/com/uiery/keep/feature/menu/MenuScreen.kt app/src/main/java/com/uiery/keep/feature/menu/MenuViewModel.kt app/src/test/java/com/uiery/keep/KeepAppNavigationPolicyTest.kt app/src/test/java/com/uiery/keep/feature/menu/MenuViewModelTest.kt
git commit -m "Route existing goals to their current detail" -m "Constraint: Keep one current-goal entry without adding a list screen
Rejected: Add goal list management | outside the requested minimal navigation scope
Confidence: high
Scope-risk: narrow
Tested: focused menu and navigation JVM tests"
```

Use Lore trailers: constraint is single current-goal entry, rejected alternative is a list screen, and `Tested:` contains the focused command.

### Task 2: Define Pure Detail Presentation

**Files:**
- Create: `app/src/test/java/com/uiery/keep/feature/goallock/GoalLockDetailPresentationTest.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailPresentation.kt`

- [ ] **Step 1: Write failing exact policy tests**

Define a sealed pure progress contract:

```kotlin
sealed interface GoalLockProgress {
    data class Pending(val daysUntilStart: Long) : GoalLockProgress
    data class Active(val remainingDaysIncludingToday: Long) : GoalLockProgress
    data class Completed(val startDate: LocalDate, val endDate: LocalDate) : GoalLockProgress
    data object EndedEarly : GoalLockProgress
}
```

Test all four variants exactly. Also assert pending/active stored-active goals can edit/end, completed/ended goals cannot, and total duration is inclusive.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.goallock.GoalLockDetailPresentationTest"
```

Expected: FAIL because the presentation types/functions are unresolved.

- [ ] **Step 3: Implement minimal pure presentation**

Use `GoalLockPolicy.runtimeStatus(goal, today.atStartOfDay())`. Keep Android resources out of this file; Compose maps the progress variants to localized strings later.

- [ ] **Step 4: Verify GREEN**

Run the Step 2 command again. Expected: `BUILD SUCCESSFUL` and every presentation test passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailPresentation.kt app/src/test/java/com/uiery/keep/feature/goallock/GoalLockDetailPresentationTest.kt
git commit -m "Make goal status presentation deterministic" -m "Constraint: Stored-active goals can be runtime pending or active
Confidence: high
Scope-risk: narrow
Tested: GoalLockDetailPresentationTest"
```

Use Lore trailers documenting stored status versus runtime status.

### Task 3: Implement Complete Edit ViewModel with TDD

**Files:**
- Create: `app/src/test/java/com/uiery/keep/feature/goallock/GoalLockEditViewModelTest.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockEditViewModel.kt`
- Modify: `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`
- Modify: `app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt`

- [ ] **Step 1: Write failing load/retry/access tests**

Cover:
- Active and pending goals initialize name, immutable start, end, exact mode, and normalized packages.
- Missing goal emits `NotFound`.
- Completed/ended goals emit `Unavailable`.
- Naturally expired stored-active goal is normalized to completed, tracks the existing completion analytics only after persistence, and exits edit.
- Fetch and normalization-update exceptions expose retry state; retry succeeds; failed normalization emits no completion analytics.
- Screen view logs `GOAL_LOCK_EDIT` once, and the new value appears once in canonical screen names.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.goallock.GoalLockEditViewModelTest" --tests "com.uiery.keep.analytics.FirebaseKeepAnalyticsTest"
```

Expected: FAIL because the edit ViewModel does not exist.

- [ ] **Step 3: Implement load state**

Add `GOAL_LOCK_EDIT = "GoalLockEditScreen"` to the canonical analytics registry. Use a deterministic `todayProvider: () -> LocalDate` injected with a default. State contains original goal, draft fields, loading/saving/error/discard flags. Keep `startDate` immutable.

- [ ] **Step 4: Add failing mutation/validation/discard tests**

Test:
- Trimmed same-name reversion clears dirty state.
- Blank name, empty packages, invalid scheduled mode, and active end date before today disable save.
- Pending goal permits end date equal to original start, but any end date before immutable `startDate` is invalid.
- Presets/stepper calculate inclusive total duration from original start.
- Unchanged custom schedule is preserved exactly.
- All-day → scheduled initializes Monday-Friday 19:00-23:00.
- Dirty back opens discard confirmation; clean back emits `NavigateBack`.
- Both system and top-bar back will call the same `requestBack()` API.

- [ ] **Step 5: Implement mutations and derived state**

Add name, duration-days, end-date, all-day, weekday-evening, replace/remove apps, request/cancel/confirm discard. Derive `isDirty` by normalized comparison; derive `isValid` and `canSave`.

- [ ] **Step 6: Add failing atomic-save tests**

Assert:
- One repository update contains all changed fields.
- ID/start/status are preserved.
- Latest missing/stored-terminal goal is rejected at pre-save fetch.
- A latest stored-active goal that is runtime-completed on the save date is normalized/completed and cannot be overwritten by the draft.
- Duplicate taps cannot cause two writes.
- Each changed field gets one existing update event with final lock mode.
- Unchanged fields get no event.
- Persistence failure retains the draft, emits zero update analytics, and emits no `Saved`.
- Success emits `Saved` only after write and analytics.

- [ ] **Step 7: Implement atomic save**

Fetch latest immediately before update; validate again; update once; emit success-only analytics; then `Saved`. Catch failure and retain draft.

- [ ] **Step 8: Verify GREEN**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.goallock.GoalLockEditViewModelTest" --tests "com.uiery.keep.analytics.FirebaseKeepAnalyticsTest"
```

Expected: `BUILD SUCCESSFUL` and all edit tests pass.

- [ ] **Step 9: Commit edit state**

```bash
git add app/src/main/java/com/uiery/keep/feature/goallock/GoalLockEditViewModel.kt app/src/test/java/com/uiery/keep/feature/goallock/GoalLockEditViewModelTest.kt app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt
git commit -m "Make goal edits atomic and lifecycle safe" -m "Constraint: Preserve immutable start dates and reject terminal goals at save time
Constraint: Emit update analytics only after one successful persistence write
Confidence: high
Scope-risk: moderate
Tested: GoalLockEditViewModelTest and canonical analytics tests"
```

Lore trailers must record single-write, immutable-start, stale-terminal, and success-only analytics constraints.

---

## Chunk 2: UI, Navigation, Atomic Detail Cutover, Verification

## File Map

| Action | File | Responsibility |
| --- | --- | --- |
| Create | `feature/goallock/GoalLockFormSupport.kt` | Weekday-evening preset and reusable end-date picker only. |
| Create | `feature/goallock/GoalLockEditNavigation.kt` | Type-safe route and destination. |
| Create | `feature/goallock/GoalLockEditScreen.kt` | KDS edit form and screen effects. |
| Modify | `GoalLockDetailNavigation.kt`, `KeepApp.kt` | Edit entry, result-based refresh, graph registration. |
| Modify | `GoalLockDetailViewModel.kt`, `GoalLockDetailScreen.kt` | Atomic cutover to read-only lifecycle and KDS detail. |
| Modify | `KeepAnalytics.kt`, analytics/navigation/accessibility tests | Screen registry and integration contracts. |
| Modify | default/Korean strings | Detail/edit localized copy. |

### Task 4: Add Edit Route and Explicit Refresh Contract

**Files:**
- Create: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockEditNavigation.kt`
- Modify: `app/src/test/java/com/uiery/keep/KeepAppNavigationPolicyTest.kt`

- [ ] **Step 1: Write failing route/analytics/result tests**

Assert:
- `GoalLockEditRoute(goalLockId)` preserves detail below it.
- A pure navigation-result key/helper contract distinguishes normal back from saved back.
- Result consumption returns true once and false after removal, so detail reload cannot duplicate.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.KeepAppNavigationPolicyTest"
```

Expected: FAIL for missing edit route or result contract.

- [ ] **Step 3: Implement route and result wiring**

Create only the serializable route, navigate function, saved-result key, and result set/consume helpers. Do not register a graph destination before the screen exists. The final detail destination will consume the result and reload in Task 7.

- [ ] **Step 4: Verify GREEN**

Run the Step 2 command again. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uiery/keep/feature/goallock/GoalLockEditNavigation.kt app/src/test/java/com/uiery/keep/KeepAppNavigationPolicyTest.kt
git commit -m "Define goal edit navigation result contract" -m "Constraint: Preserve detail below edit and consume refresh results once
Confidence: high
Scope-risk: narrow
Tested: KeepAppNavigationPolicyTest"
```

### Task 5: Define Shared Form and Accessibility/Back Contracts

**Files:**
- Create: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockFormSupport.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockCreationScreen.kt`
- Modify: `app/src/test/java/com/uiery/keep/feature/goallock/GoalLockAccessibilityDescriptionTest.kt`
- Create: `app/src/test/java/com/uiery/keep/feature/goallock/GoalLockEditScreenPolicyTest.kt`

- [ ] **Step 1: Write failing helper tests**

Add an edit accessibility builder test requiring name, date range, mode, and selected-app count exactly once. Add a pure back-source test proving system and app-bar sources both map to the same `RequestBack` intent.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.goallock.GoalLockAccessibilityDescriptionTest" --tests "com.uiery.keep.feature.goallock.GoalLockEditScreenPolicyTest"
```

Expected: FAIL for missing helpers.

- [ ] **Step 3: Extract focused form support**

Move the weekday-evening constant/factory and end-date picker/date conversion from creation into `GoalLockFormSupport.kt`. Keep this file limited to those two responsibilities; update creation to reuse it.

- [ ] **Step 4: Implement pure semantics/back helpers**

Both `BackHandler` and app-bar back call the same callback produced by the helper. Apply `clearAndSetSemantics` only to a dedicated non-interactive summary container; every text field, selector, app action, and save button retains native descendant semantics.

- [ ] **Step 5: Verify GREEN and compile**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.goallock.GoalLockAccessibilityDescriptionTest" --tests "com.uiery.keep.feature.goallock.GoalLockEditScreenPolicyTest"
./gradlew :app:compileDevDebugKotlin
```

Expected: both commands succeed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/uiery/keep/feature/goallock/GoalLockFormSupport.kt app/src/main/java/com/uiery/keep/feature/goallock/GoalLockCreationScreen.kt app/src/test/java/com/uiery/keep/feature/goallock/GoalLockAccessibilityDescriptionTest.kt app/src/test/java/com/uiery/keep/feature/goallock/GoalLockEditScreenPolicyTest.kt
git commit -m "Share focused goal lock form behavior" -m "Constraint: Combined semantics apply only to non-interactive summaries
Rejected: Add a KDS form abstraction | the behavior is goal-lock specific
Confidence: high
Scope-risk: narrow
Tested: accessibility and edit screen policy JVM tests"
```

### Task 6: Build the Complete KDS Edit Screen

**Files:**
- Create: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockEditScreen.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockEditNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/KeepApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Write failing pure render/effect policy tests**

In `GoalLockEditScreenPolicyTest`, assert exact render models for loading, retry, loaded form, save enabled/disabled, discard visibility, and effect routing (`Saved` -> set result and back; `NotFound`/`Unavailable` -> back without result). This fixed JVM lane avoids introducing a new Compose test harness; Task 8 owns device visual inspection.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.goallock.GoalLockEditScreenPolicyTest"
```

Expected: FAIL because the render/effect policy is absent.

- [ ] **Step 3: Implement the screen**

On first composition call `loadGoalLock()`. Render loading/retry/form states and collect `Saved`, `NotFound`, and `Unavailable`. Route `Saved` through the saved-result callback; terminal/missing effects navigate back without a result. Use `SetupTextField`, duration chips/stepper/end-date picker anchored to immutable start, `SetupSelectableCard`, `KeepModalBottomSheet`, category picker, removable `SetupAppRow`, one `KeepButton`, shared back callback, discard `AlertDialog`, and snackbar. Preserve exact loaded custom schedule until the user switches modes. Add `goalLockEditScreen` here and register it in `KeepApp` only after the screen exists.

- [ ] **Step 4: Add strings and remove no existing detail strings yet**

Add default English and Korean edit title/save/saving/discard/error/retry/date/mode/app copy. Other locales fall back to default.

- [ ] **Step 5: Verify GREEN**

```bash
./gradlew :app:compileDevDebugKotlin
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.goallock.GoalLockEdit*"
```

Expected: both commands succeed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/uiery/keep/feature/goallock/GoalLockEditScreen.kt app/src/main/java/com/uiery/keep/feature/goallock/GoalLockEditNavigation.kt app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailNavigation.kt app/src/main/java/com/uiery/keep/KeepApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-ko/strings.xml app/src/test/java/com/uiery/keep/feature/goallock/GoalLockEditScreenPolicyTest.kt
git commit -m "Add dedicated goal lock edit destination" -m "Constraint: Save returns a refresh result to the existing detail entry
Confidence: high
Scope-risk: moderate
Tested: edit JVM tests and dev Kotlin compilation"
```

### Task 7: Atomically Cut Detail Over to Read-Only UI

**Files:**
- Modify: `app/src/test/java/com/uiery/keep/feature/goallock/GoalLockDetailViewModelTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/feature/goallock/GoalLockAccessibilityDescriptionTest.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailViewModel.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailScreen.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/KeepApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Write failing complete detail tests**

Cover active, pending, completed, ended, missing, load retry, natural-expiration persistence failure suppressing actions, early-end persistence failure with zero analytics, successful end, and refreshed data after edit result. Update accessibility helper expectations.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.goallock.GoalLockDetail*" --tests "com.uiery.keep.feature.goallock.GoalLockAccessibilityDescriptionTest"
```

Expected: FAIL against inline-edit detail state/UI.

- [ ] **Step 3: Reduce ViewModel ownership**

Keep load/retry/request-end/cancel-end/confirm-end only. Add explicit loading and typed errors. Remove draft/update methods only now that the edit destination is complete. Catch repository failures and emit analytics only after successful writes.

- [ ] **Step 4: Replace the UI and wire edit**

Implement neutral top bar with conditional edit action, text+shape status badge, localized pure progress mapping, hero with `clearAndSetSemantics`, read-only `SetupGroupCard` rows, and app list built through `buildGoalLockSelectedAppItems` to preserve unknown-app fallback. Use non-removable `SetupAppRow`. Add destructive KDS-styled dialog and retry UI. Wire `onNavigateEdit` to the completed edit route.

- [ ] **Step 5: Remove obsolete per-field detail strings after reference search**

Use `grep -R` because the bundled `rg` is unavailable in this environment. Delete only unreferenced per-field confirmation strings.

- [ ] **Step 6: Verify GREEN**

Run the Step 2 command, then:

```bash
./gradlew :app:compileDevDebugKotlin
```

Expected: both succeed.

- [ ] **Step 7: Commit atomic cutover**

```bash
git add app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailViewModel.kt app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailScreen.kt app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailNavigation.kt app/src/main/java/com/uiery/keep/KeepApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-ko/strings.xml app/src/test/java/com/uiery/keep/feature/goallock/GoalLockDetailViewModelTest.kt app/src/test/java/com/uiery/keep/feature/goallock/GoalLockAccessibilityDescriptionTest.kt
git commit -m "Make goal detail a focused status surface" -m "Constraint: Remove inline edits only after the dedicated edit route is functional
Constraint: Suppress actions when expiration normalization cannot persist
Confidence: high
Scope-risk: moderate
Tested: detail JVM tests and dev Kotlin compilation"
```

### Task 8: Full Verification and Visual Inspection

- [ ] **Step 1: Run all JVM tests**

```bash
./gradlew :app:testDevDebugUnitTest
```

Expected: all tests PASS.

- [ ] **Step 2: Run lint**

```bash
./gradlew :app:lintDevDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL` with no new goal-lock lint errors.

- [ ] **Step 3: Build production-like APK**

```bash
./gradlew :app:assembleProdDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Inspect UI**

On emulator/device or previews verify light/dark, Korean long text, large font, scroll, TalkBack, all statuses, both dialogs, app picker, retry, and refreshed detail. Record unavailable device checks.

- [ ] **Step 5: Verify hygiene and branch pointers**

```bash
git diff --check
git status --short
test "$(git rev-parse main)" = "$(git rev-parse origin/main)"
git branch --show-current
```

Expected: no whitespace error, only intentional changes, ref comparison exits 0, and branch is `feature/goal-lock-detail-edit-ui`.

- [ ] **Step 6: Commit verification fixes only if present**

Do not create an empty commit. Use exact Lore `Tested:` and `Not-tested:` trailers.

# App shared UI components

## Purpose
Reusable Compose components that are shared across app features but are not app-agnostic enough for `core:kds`.

Use this package for UI that depends on app resources, app domain models, repositories, or app-level utilities. Pure design-system primitives still belong in `core:kds`.

## Current shared components

- `CategoryButton`: app category-selection entry button shared by Home and Lock surfaces; depends on app resources such as shield/edit icons and localized `category_selected` copy.
- `CategoryBottomSheetContent`: app-selection bottom sheet shared by onboarding, Home, and Routine surfaces; depends on app-level `appselection.InstalledAppRepository`, `AppInfo`, app strings, app icons, and KDS checkbox styling.
- `AppItem`: app-selection row used by the shared category bottom sheet.
- `SearchTextField`: app resource-backed search input used by the shared category bottom sheet.
- `PermissionSettingDialog`: app permission handoff dialog shared by onboarding and Home accessibility-permission recovery; depends on app strings and Material dialog primitives.
- `TimerPicker`: app timer picker shared by Home and Routine; depends on app string resources and app-level picker utilities.
- `CountDownContent`: lock/block countdown display shared by Lock and Block runtime surfaces; depends on app strings/plurals and app time-formatting utilities.
- `EmergencyUnlockBottomSheetContent`: emergency-unlock request flow shared by Lock and Block runtime surfaces; depends on app strings, app icon metadata resolution, KDS buttons, and privacy-safe emergency-unlock UI state.

## Ownership rules

- Do not import cross-feature UI from `com.uiery.keep.feature.home.component` or another feature-private package.
- Do not import `com.uiery.keep.feature.*` from this package; shared UI should depend on app-level/domain boundaries such as `com.uiery.keep.appselection`.
- Promote reusable app/domain/resource-bound UI here under `com.uiery.keep.ui.component`.
- Promote reusable app-agnostic primitives to `core:kds` instead. Example: shared switches use `com.uiery.kds.KeepSwitch`.
- Keep feature-specific selection controls private when they are not reused. Emergency-unlock selection controls are now app shared only because the complete request flow is reused by both Lock and Block runtime surfaces.
- Use `docs/SHARED_UI_OWNERSHIP_BOUNDARY.md` as the #492 closed baseline and future-drift source of truth when deciding whether a feature-private component should move here, move to KDS, or stay private with an explicit reason.

## Verification

Run the shared boundary guard after moving UI between feature-private, app shared, and KDS ownership:

```bash
python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v
```

# App shared UI components

## Purpose
Reusable Compose components that are shared across app features but are not app-agnostic enough for `core:kds`.

Use this package for UI that depends on app resources, app domain models, repositories, or app-level utilities. Pure design-system primitives still belong in `core:kds`.

## Current shared components

- `CategoryButton`: app category-selection entry button shared by Home and Lock surfaces; depends on app resources such as shield/edit icons and localized `category_selected` copy.
- `CategoryBottomSheetContent`: app-selection bottom sheet shared by onboarding, Home, and Routine surfaces; depends on `InstalledAppRepository`, `AppInfo`, app strings, app icons, and KDS checkbox styling.
- `AppItem`: app-selection row used by the shared category bottom sheet.
- `SearchTextField`: app resource-backed search input used by the shared category bottom sheet.
- `TimerPicker`: app timer picker shared by Home and Routine; depends on app string resources and app-level picker utilities.

## Ownership rules

- Do not import cross-feature UI from `com.uiery.keep.feature.home.component` or another feature-private package.
- Promote reusable app/domain/resource-bound UI here under `com.uiery.keep.ui.component`.
- Promote reusable app-agnostic primitives to `core:kds` instead. Example: shared switches use `com.uiery.kds.KeepSwitch`.
- Keep feature-specific selection controls private when they are not reused. For example, the emergency-unlock duration chip remains feature-private unless another feature needs the same contract.

## Verification

Run the shared boundary guard after moving UI between feature-private, app shared, and KDS ownership:

```bash
python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v
```

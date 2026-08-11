<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# KDS design system

## Purpose
Reusable KDS Compose components such as buttons, checkboxes, snackbars, modal sheets, and decorative gradient animation. AdMob SDK/runtime ownership belongs to the app monetization boundary, not KDS.

## Key Files
| File | Description |
|------|-------------|
| `KeepButton.kt` | Kotlin source for keep button. |
| `KeepBadge.kt` | Status and property badge variants. |
| `KeepCard.kt` | Semantic content surfaces. |
| `KeepChip.kt` | Action, toggle, and radio chips. |
| `KeepCheckbox.kt` | Kotlin source for keep checkbox. |
| `KeepLabel.kt` | Reusable label and metadata text hierarchy. |
| `KeepMenu.kt` | Floating action menu and semantic menu items. |
| `KeepModalBottomSheet.kt` | Kotlin source for keep modal bottom sheet. |
| `KeepProgressIndicator.kt` | Circular, linear, and step progress indicators. |
| `KeepSegmentedControl.kt` | Compact mutually exclusive view switching. |
| `KeepSelectableCard.kt` | Radio-style selectable surface. |
| `KeepSnackBar.kt` | Kotlin source for keep snack bar. |
| `RotatingCircleGradient.kt` | Kotlin source for rotating circle gradient. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `theme/` | KDS color, typography, and Material theme definitions. (see `theme/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Keep components reusable and app-agnostic; do not introduce dependencies on `:app` packages.
- Preserve KDS theme/token consistency and previewable Compose APIs where possible.
- Before changing a `Keep*.kt` component, read `../../../../../../docs/components/README.md`,
  its component guide, and the linked SEED source.
- Keep component code and its local guide in the same change. Record intentional SEED
  differences under `StopIt adaptation`.

### Testing Requirements
- ./gradlew :core:kds:testDebugUnitTest
- ./gradlew :core:kds:assembleDebug for Compose/resource changes.

### Common Patterns
- Components are named `Keep*` and wrap Material/Compose primitives with design-system defaults.
- Theme resources are split into `Color.kt`, `Type.kt`, and `Theme.kt`.

## Dependencies

### Internal
- Consumed by `:app` via `implementation(project(":core:kds"))`.

### External
- Jetpack Compose Material 3 and AndroidX UI tooling. AdMob SDK/runtime ownership belongs in the app monetization boundary.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# uiery

## Purpose
KDS organization namespace container.

## Key Files
No direct source files; this directory organizes subdirectories listed below.

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `kds/` | Reusable KDS Compose components such as buttons, checkboxes, snackbars, modal sheets, ads, and decorative gradient animation. (see `kds/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Keep components reusable and app-agnostic; do not introduce dependencies on `:app` packages.
- Preserve KDS theme/token consistency and previewable Compose APIs where possible.

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
- Jetpack Compose Material 3, AndroidX UI tooling, Google Mobile Ads where banner components are used.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

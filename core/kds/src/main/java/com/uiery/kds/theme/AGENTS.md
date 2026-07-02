<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# theme tokens

## Purpose
KDS color, typography, and Material theme definitions.

## Key Files
| File | Description |
|------|-------------|
| `Color.kt` | KDS color palette tokens. |
| `Theme.kt` | KDS Material theme wrapper. |
| `Type.kt` | KDS typography definitions. |

## Subdirectories
No documented child directories.

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
- Jetpack Compose Material 3 and AndroidX UI tooling. AdMob SDK/runtime ownership belongs in the app monetization boundary.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

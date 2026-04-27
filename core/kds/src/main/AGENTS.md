<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# main source set

## Purpose
Main KDS source set for Compose components and resources.

## Key Files
No direct source files; this directory organizes subdirectories listed below.

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `java/` | Kotlin package root for the KDS library. (see `java/AGENTS.md`) |
| `res/` | KDS Android resources. Do not place `AGENTS.md` inside Android `res/`; Gradle packages files there as resources. |

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

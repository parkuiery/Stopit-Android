<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# util

## Purpose
Small Kotlin extension utilities for build flags, context/package access, devices, density, and time formatting/math.

## Key Files
| File | Description |
|------|-------------|
| `BuildConfigExt.kt` | Kotlin source for build config ext. |
| `ContextExt.kt` | Kotlin source for context ext. |
| `DeviceExt.kt` | Kotlin source for device ext. |
| `DpExt.kt` | Kotlin source for dp ext. |
| `TimeExt.kt` | Kotlin source for time ext. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.

### Testing Requirements
- ./gradlew :app:testDevDebugUnitTest
- ./gradlew :app:assembleProdDebug
- ./gradlew :app:connectedDevDebugAndroidTest when Android services/receivers/permissions/resources require device validation.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

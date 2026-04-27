<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# DataStore preferences

## Purpose
Preferences DataStore layer for local device/session state, selected routines, tokens, and lock-control flags.

## Key Files
| File | Description |
|------|-------------|
| `DataStore.kt` | Preferences DataStore setup/constants for app settings and session state. |
| `LocalDeviceDataStore.kt` | Interface for local device/session preference operations. |
| `LocalDeviceDataStoreImpl.kt` | Preferences DataStore-backed implementation of local device/session storage. |
| `LocalDeviceDataStoreModule.kt` | Hilt binding module for local device DataStore abstraction. |
| `RoutineStore.kt` | DataStore helper for routine-related persisted state. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `di/` | Hilt bindings for preference DataStore instances. (see `di/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Keep preference keys centralized and preserve backwards compatibility for stored values.
- Expose typed operations through the DataStore abstraction rather than scattering raw key access across features.

### Testing Requirements
- ./gradlew testDebugUnitTest
- ./gradlew assembleDebug
- ./gradlew connectedAndroidTest when Android services/receivers/permissions/resources require device validation.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- Feature ViewModels and services read/write through these abstractions.

### External
- AndroidX DataStore Preferences, Kotlin coroutines/Flow.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

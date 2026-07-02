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
- For routines, Room is the authoritative source of truth; `PreferencesKey.ROUTINES` is only a compatibility cache and should be touched via `RoutineStore`, not directly from receivers/ViewModels. Follow `docs/ROUTINESTORE_COMPATIBILITY_CACHE_CONTRACT.md` (#511) for Room-vs-cache conflict-winner, rehydrate triggers, and cache retirement criteria.

### Testing Requirements
- ./gradlew :app:testDevDebugUnitTest
- ./gradlew :app:assembleProdDebug
- ./gradlew :app:connectedDevDebugAndroidTest when Android services/receivers/permissions/resources require device validation.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- Feature ViewModels and services read/write through these abstractions.

### External
- AndroidX DataStore Preferences, Kotlin coroutines/Flow.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

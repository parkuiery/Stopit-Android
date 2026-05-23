<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# dependency injection

## Purpose
Hilt bindings for preference DataStore instances.

## Key Files
| File | Description |
|------|-------------|
| `DataStoreModule.kt` | Hilt module for DataStore dependencies. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Keep preference keys centralized and preserve backwards compatibility for stored values.
- Expose typed operations through the DataStore abstraction rather than scattering raw key access across features.

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

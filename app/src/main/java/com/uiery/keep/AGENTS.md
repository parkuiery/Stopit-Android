<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# keep

## Purpose
Application package root. It hosts app entry points, top-level Compose app wiring, blocking screen plumbing, Hilt qualifiers/modules, token management, and links to data, features, services, analytics, and utilities.

## Key Files
| File | Description |
|------|-------------|
| `BlockActivity.kt` | Activity used for the in-app blocking screen presentation. |
| `BlockScreen.kt` | Compose UI for app-blocking screen. |
| `BlockViewModel.kt` | State holder for block-screen behavior. |
| `DeviceTokenManager.kt` | Persists FCM tokens locally and records the current backend-removed registration skip analytics contract. See `docs/FCM_DEVICE_REGISTRATION_CONTRACT.md`. |
| `KeepApp.kt` | Top-level Compose app shell and navigation graph wiring. |
| `KeepApplication.kt` | Application class for global initialization and dependency setup. |
| `MainActivity.kt` | Main Compose activity hosting the app UI/navigation. |
| `Picker.kt` | Shared picker UI/helper used by time or selection flows. |
| `Qualifier.kt` | Hilt qualifier annotations used by dependency bindings. |
| `TokenManagerModule.kt` | Hilt module for token-management dependencies. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `analytics/` | Analytics abstraction and Firebase-backed implementation. (see `analytics/AGENTS.md`) |
| `database/` | Room persistence layer for routines, lock history, and emergency unlock records. (see `database/AGENTS.md`) |
| `datastore/` | Preferences DataStore layer for local device/session state, selected routines, tokens, and lock-control flags. (see `datastore/AGENTS.md`) |
| `feature/` | Compose feature packages. (see `feature/AGENTS.md`) |
| `model/` | Domain and UI model classes shared by data, services, and features. (see `model/AGENTS.md`) |
| `notification/` | Notification helpers and alarm scheduling for routines and user-facing reminders. (see `notification/AGENTS.md`) |
| `receiver/` | Broadcast receivers for boot restoration and routine alarm dispatch. (see `receiver/AGENTS.md`) |
| `service/` | Long-lived Android services and service helpers for accessibility blocking, Firebase Messaging, emergency-unlock policy/state, and countdown notifications. (see `service/AGENTS.md`) |
| `util/` | Small Kotlin extension utilities for build flags, context/package access, devices, density, and time formatting/math. (see `util/AGENTS.md`) |

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

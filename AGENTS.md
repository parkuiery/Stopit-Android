<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# Keep Android

## Purpose
Keep (StopIt) is a Kotlin Android screen-time management app that blocks selected apps, manages timed and recurring lock routines, records lock history, and reports operational signals through Firebase. This root coordinates the Gradle Android app, the KDS Compose design-system module, Firebase Functions, release/build metadata, and project documentation.

## Key Files
| File | Description |
|------|-------------|
| `.firebaserc` | Tooling or VCS configuration file. |
| `.gitignore` | Tooling or VCS configuration file. |
| `build.gradle.kts` | Gradle build configuration for this module or project. |
| `check_elf_alignment.sh` | Script for checking native ELF alignment in Android artifacts. |
| `CLAUDE.md` | Claude-oriented project guidance. |
| `DESIGN.md` | Design notes and visual/product direction. |
| `firebase.json` | Firebase project configuration. |
| `gradle.properties` | Global Gradle and Android build properties. |
| `gradlew` | Unix Gradle Wrapper launcher. |
| `gradlew.bat` | Windows Gradle Wrapper launcher. |
| `local.properties` | Local developer Android/Gradle properties; do not commit sensitive changes. |
| `README.md` | Human-readable project documentation. |
| `settings.gradle.kts` | Gradle settings, plugin repositories, dependency repositories, and included modules. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `.claude/` | Claude-specific local guidance and settings for agents working in this repository. (see `.claude/AGENTS.md`) |
| `app/` | Main Android application module for package `com.uiery.keep`. (see `app/AGENTS.md`) |
| `core/` | Container for shared Gradle modules used by the application. (see `core/AGENTS.md`) |
| `docs/` | Project documentation for workflow, plans, and historical design/spec artifacts. (see `docs/AGENTS.md`) |
| `functions/` | Firebase Functions TypeScript project for operational integrations outside the Android app. (see `functions/AGENTS.md`) |
| `gradle/` | Gradle version catalog and wrapper support files shared by all modules. (see `gradle/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Respect the module boundary between `:app` and `:core:kds`; reusable visual primitives belong in KDS, app-specific orchestration belongs in `app/`.
- Do not commit local secrets from `local.properties` or flavor `google-services.json` edits unless explicitly requested.
- Keep generated/build output (`build/`, release artifacts, compiled Firebase output, caches) out of documentation and source changes.

### Testing Requirements
- ./gradlew :app:testDevDebugUnitTest for the default local JVM check.
- ./gradlew :app:assembleProdDebug for a prod-like debug artifact.
- ./gradlew :app:testProdReleaseUnitTest :app:bundleProdRelease for release-path validation when needed.
- ./gradlew :app:connectedDevDebugAndroidTest when Android framework behavior, Room migrations, services, or receivers are affected.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- `:app` - Android application module.
- `:core:kds` - shared Compose design system.
- `functions/` - Firebase Functions integration.
- `docs/` - workflow and design documentation.

### External
- Android Gradle Plugin, Kotlin, Jetpack Compose, Hilt, Room, DataStore, Firebase, Orbit MVI.

<!-- MANUAL: Existing guidance preserved from before deepinit; add custom notes below this line. -->

## Preserved Existing Guidance

# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build variants
./gradlew :app:assembleDevDebug      # Local dev debug APK
./gradlew :app:assembleProdDebug     # Prod flavor debug APK / CI smoke artifact
./gradlew :app:bundleProdRelease     # Production App Bundle

# Run tests
./gradlew :app:testDevDebugUnitTest  # Default local JVM tests
./gradlew :app:testProdReleaseUnitTest # Release-path JVM tests
./gradlew :app:connectedDevDebugAndroidTest # Instrumented tests

# Clean build
./gradlew clean :app:testDevDebugUnitTest :app:assembleProdDebug
```

Flavor note: this repo defines `dev` and `prod` flavors, so flavor-less commands such as `testDebugUnitTest`, `lintDebug`, and `assembleDebug` are ambiguous and should not be used as the default guidance.

## Architecture Overview

**Keep (StopIt)** is a screen time management and app blocking Android app. Package: `com.uiery.keep`

### Module Structure
- `app/` - Main application module
- `core/kds/` - Design system module with reusable UI components (KeepButton, KeepCheckbox, KeepSnackBar, etc.)

### Architectural Patterns

**MVI with Orbit** - Each feature uses Orbit MVI framework:
- `ViewModel` extends `ContainerHost` with MVI container
- `UiState` data class for state
- `SideEffect` sealed class for one-time events
- Intent methods modify state via `intent { reduce { } }` and emit side effects via `postSideEffect()`

**Navigation** - Jetpack Navigation Compose with type-safe routes defined as sealed objects/classes in `*Navigation.kt` files

**DI** - Hilt for dependency injection with modules in `di/` directories

### Feature Structure

Features live under `app/src/main/java/com/uiery/keep/feature/`:
- `splash/` - App initialization
- `onboarding/` - Multi-step onboarding (intro, notification, select, permission)
- `home/` - Main screen with timer and category selection
- `routine/` - Recurring schedule management
- `lock/` - Lock screen overlay + emergency unlock
- `history/` - Usage history
- `menu/` - Navigation menu

Each feature typically contains:
- `{Feature}Screen.kt` - Composable UI
- `{Feature}ViewModel.kt` - MVI state management
- `{Feature}Navigation.kt` - Route definitions
- `component/` - Feature-specific composables

### Data Layer

**Room Database** (`database/`):
- `KeepDatabase.kt` - Main database class (version 4)
- `entity/` - Room entities (RoutineEntity, LockHistoryEntity, EmergencyUnlockEntity)
- `dao/` - Data access objects
- `converter/` - Type converters for LocalTime, DayOfWeek, List<String>

**DataStore** (`datastore/`):
- Preferences storage for session data (IS_KEEP, LOCK_TIME, FCM_TOKEN, etc.)
- Keys defined in `PreferencesKey.kt`

**Network**:
- First-party Retrofit/OkHttp API layer has been removed.
- Firebase/FCM/Analytics/Crashlytics remain in use.
- No `BASE_URL` is required for current app builds.

### Key Services

- `KeepAccessibilityService` - Monitors window changes to trigger app blocking
- `KeepMessagingService` - FCM push notification handling
- `EmergencyUnlockNotificationHelper` - Countdown notifications for emergency unlock
- `EmergencyUnlockState` - In-memory singleton for instant AccessibilityService sync

### Tech Stack

- Kotlin 2.1.0, JVM target 17
- Jetpack Compose with Material 3
- Orbit MVI 9.0.0
- Room 2.7.1, DataStore 1.1.2
- Hilt 2.56.1
- Firebase (Analytics, Crashlytics, Messaging)

### Build Variants

Flavor dimension: `server`
- `dev` - Development flavor
- `prod` - Production flavor

`local.properties` is only for local Android/Gradle environment values; no backend URL or third-party monitoring values are required for current app builds.

## Additional Documentation

- [Git Workflow](docs/GIT_WORKFLOW.md) - 브랜치 전략, 커밋 컨벤션, 릴리즈 플로우
- [KDS Design System](core/kds/README.md) - 디자인 시스템 컴포넌트 및 테마

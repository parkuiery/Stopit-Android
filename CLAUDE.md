# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

Because the app module defines `dev` and `prod` flavors, flavor-less commands like `testDebugUnitTest`, `lintDebug`, and `assembleDebug` are ambiguous and should not be used as the default examples.

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

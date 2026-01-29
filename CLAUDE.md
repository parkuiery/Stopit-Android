# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build variants
./gradlew assembleDebug              # Debug APK
./gradlew assembleDev                # Dev flavor
./gradlew assembleProd               # Prod flavor
./gradlew bundleProd                 # Production App Bundle

# Run tests
./gradlew test                       # Unit tests
./gradlew connectedAndroidTest       # Instrumented tests

# Clean build
./gradlew clean build
```

## Architecture Overview

**Keep** is a screen time management and app blocking Android app. Package: `com.uiery.keep`

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
- `lock/` - Lock screen overlay
- `history/` - Usage history
- `menu/` - Navigation menu

Each feature typically contains:
- `{Feature}Screen.kt` - Composable UI
- `{Feature}ViewModel.kt` - MVI state management
- `{Feature}Navigation.kt` - Route definitions
- `component/` - Feature-specific composables

### Data Layer

**Room Database** (`database/`):
- `KeepDatabase.kt` - Main database class
- `entity/` - Room entities (e.g., `RoutineEntity`)
- `dao/` - Data access objects
- `converter/` - Type converters for LocalTime, DayOfWeek, List<String>

**DataStore** (`datastore/`):
- Preferences storage for session data (IS_KEEP, LOCK_TIME, FCM_TOKEN, etc.)
- Keys defined in `PreferencesKey.kt`

**Network** (`network/`):
- Retrofit configuration in `Retrofit.kt`
- API services: `RoutineService`, `DeviceService`
- Base URL configured per flavor (dev/prod)

### Key Services

- `KeepAccessibilityService` - Monitors window changes to trigger app blocking
- `KeepMessagingService` - FCM push notification handling

### Tech Stack

- Kotlin 2.1.0, JVM target 17
- Jetpack Compose with Material 3
- Orbit MVI 9.0.0
- Room 2.7.1, DataStore 1.1.2
- Retrofit 3.0.0, OkHttp 4.12.0
- Hilt 2.56.1
- Firebase (Analytics, Crashlytics, Messaging)
- Datadog for session replay

### Build Variants

Flavor dimension: `server`
- `dev` - Development server (BASE_URL from local.properties)
- `prod` - Production server (BASE_URL from local.properties)

Environment variables configured in `local.properties` and injected via `buildConfigField`.

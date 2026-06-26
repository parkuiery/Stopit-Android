<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# lock feature-private components

## Purpose
Feature-private component package for lock-only UI. The shared Lock/Block countdown and emergency-unlock bottom sheet primitives no longer live here; #876 moved them to app shared UI under `app/src/main/java/com/uiery/keep/ui/component`.

## Key Files
No Kotlin source files are currently owned by this package.

Shared Lock/Block UI ownership now lives in app shared UI:

| Shared file | Current owner |
|------|-------------|
| `app/src/main/java/com/uiery/keep/ui/component/CountDownContent.kt` | Countdown display shared by Lock and Block runtime surfaces. |
| `app/src/main/java/com/uiery/keep/ui/component/EmergencyUnlockBottomSheetContent.kt` | Emergency-unlock bottom sheet flow shared by Lock and Block runtime surfaces. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Follow the existing Orbit MVI pattern: immutable `UiState`, one-time `SideEffect`, and intent methods that reduce state or post effects.
- Keep Composable screens stateless where practical; route user events into the feature ViewModel.
- Do not reintroduce `CountDownContent.kt` or `EmergencyUnlockBottomSheetContent.kt` here. If a lock-only UI piece is added later, keep it feature-private only when no other feature/app-root blocking surface imports it.

### Testing Requirements
- ./gradlew :app:testDevDebugUnitTest
- ./gradlew :app:assembleProdDebug
- ./gradlew :app:connectedDevDebugAndroidTest when Android services/receivers/permissions/resources require device validation.

### Common Patterns
- Feature package pattern: `{Feature}Navigation.kt`, `{Feature}Screen.kt`, `{Feature}ViewModel.kt`, optional `component/` package.
- Compose + Orbit MVI are the default interaction model.
- Navigation routes are type-safe Kotlin objects/classes rather than raw string constants.

## Dependencies

### Internal
- `app/src/main/java/com/uiery/keep/model/` for domain models.
- `app/src/main/java/com/uiery/keep/datastore/` and `database/` for persistence as needed.
- `core/kds/` for reusable UI components.

### External
- Jetpack Compose, Navigation Compose, Orbit MVI, Hilt where injected.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

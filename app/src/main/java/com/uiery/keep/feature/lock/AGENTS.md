<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# lock feature

## Purpose
Lock overlay feature shown when a blocked app is opened. Handles lock-specific state/navigation and uses app shared UI for the countdown display and emergency-unlock user flow.

## Key Files
| File | Description |
|------|-------------|
| `LockNavigation.kt` | Type-safe navigation route and graph wiring for Lock. |
| `LockScreen.kt` | Compose screen for the Lock flow. |
| `LockViewModel.kt` | Orbit MVI view-model/state holder for Lock. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `component/` | Feature-private lock-only UI pieces. Countdown and emergency-unlock bottom sheet primitives moved to app shared UI; see `component/AGENTS.md`. |

## For AI Agents

### Working In This Directory
- Follow the existing Orbit MVI pattern: immutable `UiState`, one-time `SideEffect`, and intent methods that reduce state or post effects.
- Keep Composable screens stateless where practical; route user events into the feature ViewModel.
- Place only lock-specific feature-private UI pieces in `component/`; shared Lock/Block UI such as `CountDownContent` and `EmergencyUnlockBottomSheetContent` belongs in `app/src/main/java/com/uiery/keep/ui/component` per `docs/SHARED_UI_OWNERSHIP_BOUNDARY.md` (#876 cleaned baseline).

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

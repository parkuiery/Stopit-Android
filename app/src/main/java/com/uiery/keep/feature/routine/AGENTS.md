<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# routine feature

## Purpose
Recurring routine management feature, including list/detail UI and bottom-sheet state for routine creation/editing.

## Key Files
| File | Description |
|------|-------------|
| `RoutineBottomSheetViewModel.kt` | Orbit MVI view-model/state holder for RoutineBottomSheet. |
| `RoutineNavigation.kt` | Type-safe navigation route and graph wiring for Routine. |
| `RoutineScreen.kt` | Compose screen for the Routine flow. |
| `RoutineViewModel.kt` | Orbit MVI view-model/state holder for Routine. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `component/` | Routine-specific Compose controls for names, days, times, selected apps, empty states, and bottom-sheet content. (see `component/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Follow the existing Orbit MVI pattern: immutable `UiState`, one-time `SideEffect`, and intent methods that reduce state or post effects.
- Keep Composable screens stateless where practical; route user events into the feature ViewModel.
- Place feature-private UI pieces in `component/` instead of expanding screen files indefinitely.

### Testing Requirements
- ./gradlew testDebugUnitTest
- ./gradlew assembleDebug
- ./gradlew connectedAndroidTest when Android services/receivers/permissions/resources require device validation.

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

<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# home feature

## Purpose
Main home feature for selecting blocked apps/categories, configuring timers, and starting/stopping Keep sessions.

## Key Files
| File | Description |
|------|-------------|
| `HomeNavigation.kt` | Type-safe navigation route and graph wiring for Home. |
| `HomeScreen.kt` | Compose screen for the Home flow. |
| `HomeViewModel.kt` | Orbit MVI view-model/state holder for Home. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `component/` | Home-specific Compose UI pieces such as timer pickers, category controls, search fields, app rows, bottom sheets, and segmented controls. (see `component/AGENTS.md`) |

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

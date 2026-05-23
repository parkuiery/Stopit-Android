<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# feature UI

## Purpose
Compose feature packages. Each feature generally follows Orbit MVI with a `Screen`, `ViewModel`, and type-safe Navigation route plus smaller composables in `component/`.

## Key Files
No direct source files; this directory organizes subdirectories listed below.

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `devtool/` | Developer-tool feature for debug/dev builds and internal diagnostics. (see `devtool/AGENTS.md`) |
| `history/` | Legacy or focused history feature for displaying individual lock history items. (see `history/AGENTS.md`) |
| `home/` | Main home feature for selecting blocked apps/categories, configuring timers, and starting/stopping Keep sessions. (see `home/AGENTS.md`) |
| `lock/` | Lock overlay feature shown when a blocked app is opened. (see `lock/AGENTS.md`) |
| `lockhistory/` | Lock-history overview feature for reviewing blocked app sessions and weekly summaries. (see `lockhistory/AGENTS.md`) |
| `menu/` | Menu/settings feature for navigation and toggles outside the primary home flow. (see `menu/AGENTS.md`) |
| `onboarding/` | Onboarding route graph for the first-run flow across intro, notification, app-selection, and permission steps. (see `onboarding/AGENTS.md`) |
| `routine/` | Recurring routine management feature, including list/detail UI and bottom-sheet state for routine creation/editing. (see `routine/AGENTS.md`) |
| `splash/` | Splash/startup route that initializes local state and directs users into onboarding or the main app. (see `splash/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Follow the existing Orbit MVI pattern: immutable `UiState`, one-time `SideEffect`, and intent methods that reduce state or post effects.
- Keep Composable screens stateless where practical; route user events into the feature ViewModel.
- Place feature-private UI pieces in `component/` instead of expanding screen files indefinitely.

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

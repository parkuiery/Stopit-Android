<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# onboarding feature

## Purpose
Onboarding route graph for the first-run flow across intro, notification, app-selection, and permission steps.

## Key Files
| File | Description |
|------|-------------|
| `OnboardingNavigation.kt` | Type-safe navigation route and graph wiring for Onboarding. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `intro/` | Introductory onboarding screen and state for explaining the app value proposition. (see `intro/AGENTS.md`) |
| `notification/` | Onboarding step for notification setup and related analytics/state handling. (see `notification/AGENTS.md`) |
| `permission/` | Onboarding step for requesting and validating required Android permissions. (see `permission/AGENTS.md`) |
| `select/` | Onboarding step for selecting apps to block. (see `select/AGENTS.md`) |

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

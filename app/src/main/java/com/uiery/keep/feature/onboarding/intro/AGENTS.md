<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# intro onboarding

## Purpose
Introductory onboarding screen and state for explaining the app value proposition.

## Key Files
| File | Description |
|------|-------------|
| `IntroNavigation.kt` | Type-safe navigation route and graph wiring for Intro. |
| `IntroScreen.kt` | Compose screen for the Intro flow. |
| `IntroViewModel.kt` | Orbit MVI view-model/state holder for Intro. |

## Subdirectories
No documented child directories.

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

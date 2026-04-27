<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# Compose components

## Purpose
Lock-history summary cards, tab controls, session rows, top-app widgets, and week calendar components.

## Key Files
| File | Description |
|------|-------------|
| `LockHistorySessionItem.kt` | Kotlin source for lock history session item. |
| `LockHistorySummaryCard.kt` | Kotlin source for lock history summary card. |
| `LockHistoryTab.kt` | Kotlin source for lock history tab. |
| `LockHistoryTopApps.kt` | Kotlin source for lock history top apps. |
| `LockHistoryWeekCalendar.kt` | Kotlin source for lock history week calendar. |

## Subdirectories
No documented child directories.

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

<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# notifications

## Purpose
Notification helpers and alarm scheduling for routines and user-facing reminders.

## Key Files
| File | Description |
|------|-------------|
| `NotificationHelper.kt` | Creates and manages Android notification channels/notifications. |
| `RoutineScheduler.kt` | Schedules/cancels routine alarms and notification timing. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Account for Android lifecycle/background restrictions and permission requirements before changing behavior.
- Keep service/receiver logic thin when possible and move pure decisions into testable policy/helpers.

### Testing Requirements
- ./gradlew :app:testDevDebugUnitTest
- ./gradlew :app:assembleProdDebug
- ./gradlew :app:connectedDevDebugAndroidTest when Android services/receivers/permissions/resources require device validation.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

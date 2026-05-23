<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# Android services

## Purpose
Long-lived Android services and service helpers for accessibility blocking, Firebase Messaging, emergency-unlock policy/state, and countdown notifications.

## Key Files
| File | Description |
|------|-------------|
| `EmergencyUnlockNotificationHelper.kt` | Notification helper for emergency-unlock countdown/status updates. |
| `EmergencyUnlockPolicy.kt` | Pure policy logic for emergency-unlock limits and eligibility. |
| `EmergencyUnlockState.kt` | In-memory state bridge for emergency unlock status used by services/UI. |
| `KeepAccessibilityService.kt` | Accessibility service that observes foreground windows and triggers blocking behavior. |
| `KeepMessagingService.kt` | Firebase Messaging service for FCM token/message handling. |

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

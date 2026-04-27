<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# Android services

## Purpose
Unit tests for service-adjacent policy logic such as emergency unlock eligibility/cooldowns.

## Key Files
| File | Description |
|------|-------------|
| `EmergencyUnlockPolicyTest.kt` | Automated test for EmergencyUnlockPolicy behavior. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Account for Android lifecycle/background restrictions and permission requirements before changing behavior.
- Keep service/receiver logic thin when possible and move pure decisions into testable policy/helpers.

### Testing Requirements
- ./gradlew testDebugUnitTest or ./gradlew test for local JVM tests.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

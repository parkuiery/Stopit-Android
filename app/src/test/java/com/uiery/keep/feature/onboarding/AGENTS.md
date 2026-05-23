<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# onboarding feature

## Purpose
Onboarding feature unit tests, especially analytics emission from view-model actions.

## Key Files
| File | Description |
|------|-------------|
| `OnboardingAnalyticsViewModelTest.kt` | Automated test for OnboardingAnalyticsViewModel behavior. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Mirror production package structure for discoverability.
- Prefer focused tests around pure logic or migration behavior rather than broad brittle UI assertions.

### Testing Requirements
- ./gradlew :app:testDevDebugUnitTest for the default local JVM tests.
- ./gradlew test only when intentionally running the repository-wide JVM suite.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

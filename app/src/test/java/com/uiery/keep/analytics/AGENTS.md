<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# analytics

## Purpose
Unit tests for analytics abstractions and Firebase analytics event behavior.

## Key Files
| File | Description |
|------|-------------|
| `FirebaseKeepAnalyticsTest.kt` | Automated test for FirebaseKeepAnalytics behavior. |
| `TrackedBannerAdTest.kt` | Automated test for TrackedBannerAd behavior. |

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

- Keep analytics tests aligned with `docs/ANALYTICS_EVENT_DICTIONARY.md`; new event names/params should land with focused test coverage rather than doc-only promises.
- If a test adds or changes a queryable custom-event parameter, also update `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` so manual GA4 Admin registration and metadata re-check steps do not drift from the code/test contract.

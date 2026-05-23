<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# unit tests

## Purpose
Local JVM unit-test source set for model mapping, analytics wrappers, onboarding view-model analytics, emergency-unlock policy, and utilities.

## Key Files
No direct source files; this directory organizes subdirectories listed below.

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `java/` | Kotlin package root for local JVM tests. (see `java/AGENTS.md`) |

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

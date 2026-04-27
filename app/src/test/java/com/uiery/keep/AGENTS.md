<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# keep

## Purpose
Application unit-test package root.

## Key Files
| File | Description |
|------|-------------|
| `ExampleUnitTest.kt` | Automated test for ExampleUnit behavior. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `analytics/` | Unit tests for analytics abstractions and Firebase analytics event behavior. (see `analytics/AGENTS.md`) |
| `feature/` | Feature unit-test package container. (see `feature/AGENTS.md`) |
| `model/` | Unit tests for model/entity mapping behavior. (see `model/AGENTS.md`) |
| `service/` | Unit tests for service-adjacent policy logic such as emergency unlock eligibility/cooldowns. (see `service/AGENTS.md`) |
| `util/` | Unit tests for utility extensions. (see `util/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Mirror production package structure for discoverability.
- Prefer focused tests around pure logic or migration behavior rather than broad brittle UI assertions.

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

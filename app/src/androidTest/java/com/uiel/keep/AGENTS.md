<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# keep

## Purpose
Legacy sample instrumented test package.

## Key Files
| File | Description |
|------|-------------|
| `ExampleInstrumentedTest.kt` | Automated test for ExampleInstrumented behavior. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Mirror production package structure for discoverability.
- Prefer focused tests around pure logic or migration behavior rather than broad brittle UI assertions.

### Testing Requirements
- ./gradlew connectedAndroidTest for Room migrations or Android framework integration.
- ./gradlew test for pure logic touched by the same change.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

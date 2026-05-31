<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# Gradle configuration

## Purpose
Gradle version catalog and wrapper support files shared by all modules.

## Key Files
| File | Description |
|------|-------------|
| `libs.versions.toml` | Gradle version catalog for plugin and library coordinates. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `wrapper/` | Gradle Wrapper binary/properties. |

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.

### Testing Requirements
- ./gradlew test for repository-wide JVM tests when behavior changes.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

## Manual Notes

- `gradle/libs.versions.toml` is the preferred source of truth for library/plugin versions across `:app` and `:core:kds`.
- Before claiming direct-version drift is gone, inspect both `app/build.gradle.kts` and `core/kds/build.gradle.kts`; KDS can still carry leftover string-pinned dependencies even after app cleanup.
- If a dependency remains direct-versioned because no catalog alias exists yet (for example a lifecycle artifact), document that exception in the maintenance issue or runbook instead of silently normalizing around it.

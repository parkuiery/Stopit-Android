<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# com.uiery.keep.database.KeepDatabase

## Purpose
Room schema snapshots for `KeepDatabase` versions 1 through 4. These files support migration validation and should change only when the database schema or version changes.

## Key Files
| File | Description |
|------|-------------|
| `1.json` | Exported Room schema JSON for database version 1. |
| `2.json` | Exported Room schema JSON for database version 2. |
| `3.json` | Exported Room schema JSON for database version 3. |
| `4.json` | Exported Room schema JSON for database version 4. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.

### Testing Requirements
- ./gradlew testDebugUnitTest
- ./gradlew assembleDebug
- ./gradlew connectedAndroidTest when Android services/receivers/permissions/resources require device validation.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

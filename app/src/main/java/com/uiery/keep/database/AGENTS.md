<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# database

## Purpose
Room persistence layer for routines, lock history, and emergency unlock records.

## Key Files
| File | Description |
|------|-------------|
| `KeepDatabase.kt` | Room database class declaring entities, DAOs, converters, version, and migrations. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `converter/` | Room type converters for Java/Kotlin time types and list serialization used by database entities. (see `converter/AGENTS.md`) |
| `dao/` | Room DAO interfaces for routines, lock-history sessions, and emergency-unlock records. (see `dao/AGENTS.md`) |
| `di/` | Hilt database providers and DAO bindings. (see `di/AGENTS.md`) |
| `entity/` | Room entity definitions that define persisted table shape and migration-sensitive fields. (see `entity/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Treat entity and migration changes as schema changes: update `KeepDatabase`, export schemas, and add/adjust migration tests.
- Prefer explicit DAO queries and suspend/Flow APIs consistent with existing Room usage.

### Testing Requirements
- ./gradlew :app:connectedDevDebugAndroidTest for Room migrations or Android framework integration.
- ./gradlew :app:testDevDebugUnitTest for pure logic touched by the same change.

### Common Patterns
- Room entities live in `entity/`, DAO interfaces in `dao/`, converters in `converter/`, and providers in `di/`.
- Schema JSON under `app/schemas/` is part of the migration contract.

## Dependencies

### Internal
- `app/schemas/` for exported schema history.
- `app/src/androidTest/.../database/` for migration tests.
- `model/` for domain mappings.

### External
- Room 2.7.x, Kotlinx datetime/time converters.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

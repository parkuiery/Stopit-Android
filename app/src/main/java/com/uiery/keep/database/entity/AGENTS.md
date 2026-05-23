<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# Room entities

## Purpose
Room entity definitions that define persisted table shape and migration-sensitive fields.

## Key Files
| File | Description |
|------|-------------|
| `EmergencyUnlockEntity.kt` | Room entity for emergencyunlock records. |
| `LockHistoryEntity.kt` | Room entity for lockhistory records. |
| `RoutineEntity.kt` | Room entity for routine records. |

## Subdirectories
No documented child directories.

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

<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# Room DAOs

## Purpose
Room DAO interfaces for routines, lock-history sessions, and emergency-unlock records.

## Key Files
| File | Description |
|------|-------------|
| `EmergencyUnlockDao.kt` | Room DAO for emergencyunlock persistence operations. |
| `LockHistoryDao.kt` | Room DAO for lockhistory persistence operations. |
| `RoutineDao.kt` | Room DAO for routine persistence operations. |

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

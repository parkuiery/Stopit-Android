<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# domain models

## Purpose
Domain and UI model classes shared by data, services, and features.

## Key Files
| File | Description |
|------|-------------|
| `AppInfo.kt` | Model for installed/selectable app metadata. |
| `LockHistoryModel.kt` | Domain model for lock history summaries/sessions. |
| `RoutineModel.kt` | Domain model for routines. Room entity mapping helpers live under `database/mapper/`. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.
- Do not import Room entities from shared models. Entity/model mappers are owned by `com.uiery.keep.database.mapper` so the model package stays persistence-agnostic.

### Testing Requirements
- ./gradlew :app:testDevDebugUnitTest
- ./gradlew :app:assembleProdDebug
- ./gradlew :app:connectedDevDebugAndroidTest when Android services/receivers/permissions/resources require device validation.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# dev source set

## Purpose
Development flavor source set. CI/Release QA may restore `google-services.json` here for the dev flavor, but the workflow-specific restore matrix is owned by `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md` rather than this directory alone.

## Key Files
| File | Description |
|------|-------------|
| `google-services.json` | Firebase/Google services configuration for this flavor when restored locally or by CI. Check `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md` before assuming which workflow writes this file. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.

### Testing Requirements
- ./gradlew :app:testDevDebugUnitTest
- ./gradlew :app:assembleDevDebug
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

<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# prod source set

## Purpose
Production flavor source set. Release Build/Play Deploy restore `google-services.json` here. Android CI/Release QA restore `GOOGLE_SERVICES_JSON` to prod while `GOOGLE_SERVICES_JSON_DEV` owns dev; the workflow-specific restore matrix is owned by `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`.

## Key Files
| File | Description |
|------|-------------|
| `google-services.json` | Firebase/Google services configuration for this flavor when restored locally or by CI/CD. Check `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md` before assuming which workflow writes this file. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.

### Testing Requirements
- ./gradlew :app:testProdReleaseUnitTest
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

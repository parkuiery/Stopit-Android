<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# source sets

## Purpose
Android source-set container for main app code, flavor-specific Firebase configuration, unit tests, and instrumented Android tests.

## Key Files
No direct source files; this directory organizes subdirectories listed below.

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `androidTest/` | Instrumented Android test source set for tests that need an Android runtime, Room migration helpers, or device/emulator APIs. |
| `dev/` | Development flavor source set. (see `dev/AGENTS.md`) |
| `main/` | Primary Android source set containing the manifest, Kotlin application code, resources, launcher artwork, services, receivers, and Compose UI. (see `main/AGENTS.md`) |
| `prod/` | Production flavor source set. (see `prod/AGENTS.md`) |
| `test/` | Local JVM unit-test source set for model mapping, analytics wrappers, onboarding view-model analytics, emergency-unlock policy, and utilities. |

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.
- `dev/`와 `prod/` source set의 `google-services.json`은 디렉터리 구조만 보고 상주 파일처럼 가정하지 않는다. Android CI / Release QA는 `GOOGLE_SERVICES_JSON_DEV`를 dev에, `GOOGLE_SERVICES_JSON`를 prod에 복원하고, Release Build / Play Deploy non-production build/upload는 prod만 복원한다. Play Deploy production promotion은 기존 internal release를 승격하므로 `GOOGLE_SERVICES_JSON`/`ANDROID_*`를 복원하지 않는다. workflow별 restore matrix와 helper 범위는 `../../docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`를 기준으로 확인한다.

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

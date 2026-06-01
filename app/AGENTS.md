<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# app module

## Purpose
Main Android application module for package `com.uiery.keep`. It contains flavor-specific configuration, Room schemas, Android source sets, ProGuard rules, and dependencies for Compose, Orbit MVI, Hilt, Room, Firebase, and the local KDS module.

## Key Files
| File | Description |
|------|-------------|
| `.gitignore` | Tooling or VCS configuration file. |
| `build.gradle.kts` | Gradle build configuration for this module or project. |
| `proguard-rules.pro` | R8/ProGuard keep and optimization rules for release builds. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `schemas/` | Versioned Room schema exports used by migration tests and database review. (see `schemas/AGENTS.md`) |
| `src/` | Android source-set container for main app code, flavor-specific Firebase configuration, unit tests, and instrumented Android tests. (see `src/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.
- flavor source-set의 `google-services.json`은 디렉터리만 보고 상주 파일처럼 가정하지 않는다. Android CI / Release QA / Release Build / Play Deploy의 restore 차이는 `../docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`를 기준으로 확인한다.

### Testing Requirements
- ./gradlew :app:testDevDebugUnitTest
- ./gradlew :app:assembleProdDebug
- ./gradlew :app:testProdReleaseUnitTest :app:bundleProdRelease when validating the release path.
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

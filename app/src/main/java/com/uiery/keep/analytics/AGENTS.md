<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# analytics

## Purpose
Analytics abstraction and Firebase-backed implementation. Use this package for event tracking contracts, Firebase adapters, Hilt bindings, and ad tracking wrappers.

## Key Files
| File | Description |
|------|-------------|
| `AnalyticsBackend.kt` | Backend interface for analytics event dispatch. |
| `AnalyticsModule.kt` | Hilt bindings for analytics abstractions. |
| `FirebaseAnalyticsBackend.kt` | Firebase-backed analytics backend implementation. |
| `FirebaseKeepAnalytics.kt` | Keep analytics facade implementation backed by Firebase. |
| `FirebaseModule.kt` | Firebase-related Hilt providers. |
| `KeepAnalytics.kt` | Analytics event API used by app features. |
| `TrackedBannerAd.kt` | Ad composable/wrapper that records analytics around banner visibility/interactions. |

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

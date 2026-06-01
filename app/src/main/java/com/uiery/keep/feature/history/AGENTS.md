<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-06-01 -->

# history navigation shim

## Purpose
This package is now a legacy navigation compatibility shim. The user-facing history surface is `feature/lockhistory` and legacy callers must route to `LockHistoryRoute` instead of registering a separate History graph node.

## Key Files
| File | Description |
|------|-------------|
| `HistoryNavigation.kt` | Legacy `navigateToHistory(...)` helper that forwards to the canonical lock-history route. |

## Subdirectories
No active child source directories.

## For AI Agents

### Working In This Directory
- Do not reintroduce `HistoryScreen`, `HistoryViewModel`, or a separate `HistoryRoute` without a product decision.
- Add new history UI or analytics work under `feature/lockhistory`, not this legacy shim package.
- Keep any compatibility helper routing to `LockHistoryRoute` so GA4 screen_view remains `LockHistoryScreen`.

### Testing Requirements
- `./gradlew :app:testDevDebugUnitTest`
- `./gradlew :app:assembleProdDebug`

### Common Patterns
- Prefer the canonical `navigateToLockHistory(...)` helper for new call sites.
- Keep legacy helpers thin and side-effect free.

## Dependencies

### Internal
- `app/src/main/java/com/uiery/keep/feature/lockhistory/` for the canonical history surface.

### External
- Jetpack Navigation Compose only where compatibility helpers need route types.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

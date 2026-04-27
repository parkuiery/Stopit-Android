<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# Firebase functions

## Purpose
Firebase Functions TypeScript project for operational integrations outside the Android app. It currently forwards Crashlytics alerts to Discord using a secret webhook.

## Key Files
| File | Description |
|------|-------------|
| `.gitignore` | Tooling or VCS configuration file. |
| `package-lock.json` | Locked npm dependency graph for reproducible Firebase Functions installs. |
| `package.json` | Node package metadata and Firebase Functions scripts/dependencies. |
| `README.md` | Human-readable project documentation. |
| `tsconfig.json` | TypeScript compiler configuration for Firebase Functions. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `src/` | TypeScript source for Firebase Functions exports. (see `src/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Keep Firebase Functions handlers small and push formatting/helper logic into pure functions that can be tested locally.
- Never hardcode secret webhook values; use Firebase secret parameters.

### Testing Requirements
- cd functions && npm run build
- Use Firebase emulator/manual deployment checks only when changing deployed behavior.

### Common Patterns
- Firebase v2 Crashlytics alert handlers export one function per alert type.
- Discord messages are formatted from shared helper functions and posted through a secret webhook.

## Dependencies

### Internal
- Firebase project configuration in root `firebase.json`.

### External
- firebase-functions v2, Firebase Crashlytics alerts, Discord webhook HTTP API.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

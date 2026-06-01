<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# source sets

## Purpose
TypeScript source for Firebase Functions exports.

## Key Files
| File | Description |
|------|-------------|
| `index.ts` | Firebase Functions entry point and exported function handlers. |

## Subdirectories
No documented child directories.

## For AI Agents

### Working In This Directory
- Keep Firebase Functions handlers small and push formatting/helper logic into pure functions that can be tested locally.
- Never hardcode secret webhook values; use Firebase secret parameters.
- `index.ts`의 Discord deploy interaction 경로를 만질 때는 GitHub Actions repo secret과 Firebase Functions secret을 섞지 말고 `../README.md`와 `../../docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`를 함께 확인한다.

### Testing Requirements
- cd functions && npm run build
- Secret ownership/consumer 경계가 바뀌면 `python3 -m unittest scripts.tests.test_play_deploy_secret_contract_runbook -v`로 runbook ↔ Functions 문서 drift도 함께 점검한다.
- Use Firebase emulator/manual deployment checks only when changing deployed behavior.

### Common Patterns
- Firebase v2 Crashlytics alert handlers export one function per alert type.
- Discord messages are formatted from shared helper functions and posted through a secret webhook.
- Deploy approval interactions는 `DISCORD_PUBLIC_KEY`, `DISCORD_DEPLOY_CHANNEL_ID`, 허용 role/user ID, `GITHUB_ACTIONS_DISPATCH_TOKEN` 같은 Firebase Functions secrets를 직접 소비한다.

## Dependencies

### Internal
- Firebase project configuration in root `firebase.json`.

### External
- firebase-functions v2, Firebase Crashlytics alerts, Discord webhook HTTP API.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->

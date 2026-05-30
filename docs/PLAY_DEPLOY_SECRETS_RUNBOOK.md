# Play deploy secret contract runbook

이 문서는 Stopit Android의 Play 배포 시크릿 계약을 **helper / workflow / Firebase Functions** 기준으로 한 곳에 정리한 임시 source of truth다.

왜 별도 문서가 필요한가:
- GitHub Actions용 secret과 Firebase Functions용 secret의 소유 경계가 다르다.
- `GOOGLE_SERVICES_JSON`은 모든 workflow에서 같은 방식으로 복원되지 않는다.
- `scripts/setup-play-deploy-secrets.sh`는 Android/Play 배포용 helper이고, Discord deploy 알림 secret은 별도 helper가 더 안전하다.

## 1. Secret ownership matrix

### GitHub Actions repository secrets

| Secret | 설정 helper / 방법 | 사용하는 경로 | 실제 복원/사용 위치 |
| --- | --- | --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `scripts/setup-play-deploy-secrets.sh` | Release Build, Play Deploy | runner temp keystore 파일 |
| `ANDROID_KEYSTORE_PASSWORD` | `scripts/setup-play-deploy-secrets.sh` | Release Build, Play Deploy | Gradle signing env |
| `ANDROID_KEY_ALIAS` | `scripts/setup-play-deploy-secrets.sh` | Release Build, Play Deploy | Gradle signing env |
| `ANDROID_KEY_PASSWORD` | `scripts/setup-play-deploy-secrets.sh` | Release Build, Play Deploy | Gradle signing env |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | `scripts/setup-play-deploy-secrets.sh` | Version Guard, Play Deploy | runner temp service-account JSON |
| `GOOGLE_SERVICES_JSON` | `scripts/setup-play-deploy-secrets.sh` | Android CI, Release QA, Release Build, Play Deploy | 아래 workflow matrix 참고 |
| `DISCORD_BOT_TOKEN` | `scripts/setup-discord-deploy-secrets.sh` 또는 `gh secret set` | Play Deploy | `scripts/notify-discord-deploy.py` |
| `DISCORD_DEPLOY_CHANNEL_ID` | `scripts/setup-discord-deploy-secrets.sh` 또는 `gh secret set` | Play Deploy, Firebase Functions | deploy 알림 채널 / Discord interaction 채널 검증 |

### Firebase Functions secrets

이 secret들은 GitHub Actions helper가 아니라 Firebase Functions 배포 절차의 일부다.

| Secret | 설정 방법 | 사용하는 경로 |
| --- | --- | --- |
| `DISCORD_WEBHOOK_URL` | `firebase functions:secrets:set DISCORD_WEBHOOK_URL` | Crashlytics Discord alerts |
| `DISCORD_PUBLIC_KEY` | `firebase functions:secrets:set DISCORD_PUBLIC_KEY` | `promoteProductionFromDiscord` signature 검증 |
| `DISCORD_DEPLOY_CHANNEL_ID` | `firebase functions:secrets:set DISCORD_DEPLOY_CHANNEL_ID` | deploy approval interaction channel 검증 |
| `DISCORD_DEPLOY_ALLOWED_ROLE_IDS` | `firebase functions:secrets:set DISCORD_DEPLOY_ALLOWED_ROLE_IDS` | production promotion 권한 검증 |
| `DISCORD_DEPLOY_ALLOWED_USER_IDS` | `firebase functions:secrets:set DISCORD_DEPLOY_ALLOWED_USER_IDS` | production promotion 권한 검증 |
| `GITHUB_ACTIONS_DISPATCH_TOKEN` | `firebase functions:secrets:set GITHUB_ACTIONS_DISPATCH_TOKEN` | Functions -> GitHub Actions workflow dispatch |

중요:
- `DISCORD_DEPLOY_CHANNEL_ID`는 GitHub Actions와 Firebase Functions **양쪽에 모두 필요**하다.
- 같은 이름이지만 저장 위치와 사용 주체가 다르므로, 한쪽만 설정했다고 전체 deploy 계약이 완성되지 않는다.

## 2. Workflow restore / usage matrix

| Workflow | `GOOGLE_SERVICES_JSON` 동작 | 기타 핵심 secret |
| --- | --- | --- |
| `android-ci.yml` | `app/src/dev/google-services.json`, `app/src/prod/google-services.json` 둘 다 복원 | 없음 |
| `release-qa.yml` | `app/src/dev/google-services.json`, `app/src/prod/google-services.json` 둘 다 복원 | 없음 |
| `release-build.yml` | `app/src/prod/google-services.json`만 복원 | `ANDROID_*` |
| `play-deploy.yml` | `app/src/prod/google-services.json`만 복원 | `ANDROID_*`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `DISCORD_BOT_TOKEN`, `DISCORD_DEPLOY_CHANNEL_ID` |
| `version-guard.yml` | 사용 안 함 | `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` |
| `functions/README.md`에 설명된 Discord promotion flow | GitHub secret 아님 | Firebase Functions secrets 사용 |

해석 규칙:
- `GOOGLE_SERVICES_JSON`을 “prod 전용 secret”으로 이해하면 Android CI / Release QA 실패 원인을 놓치기 쉽다.
- 더 정확한 계약은 **하나의 secret 값을 workflow마다 다른 source set에 복원한다**이다.
- 현재 steady-state에서는 Android CI / Release QA가 dev+prod 둘 다를 요구하고, Release Build / Play Deploy는 prod만 요구한다.

## 3. 권장 setup 순서

### A. Android / Play build-upload secrets

```bash
scripts/setup-play-deploy-secrets.sh \
  --keystore /path/to/upload-key.jks \
  --service-account /path/to/google-play-service-account.json \
  --alias <upload-key-alias> \
  --google-services app/src/prod/google-services.json
```

이 helper가 설정하는 범위:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
- `GOOGLE_SERVICES_JSON`

### B. GitHub Actions Discord deploy notification secrets

```bash
DISCORD_BOT_TOKEN=<discord-bot-token> \
DISCORD_DEPLOY_CHANNEL_ID=<deploy-channel-id> \
  scripts/setup-discord-deploy-secrets.sh
```

이 helper는 다음만 설정한다:
- `DISCORD_BOT_TOKEN`
- `DISCORD_DEPLOY_CHANNEL_ID`

의도적인 범위 제한:
- 이 helper는 GitHub Actions의 deploy 알림/approval card 발송 계약만 다룬다.
- Firebase Functions deploy approval secret은 아래 수동 단계에서 따로 설정한다.

### C. Firebase Functions production promotion secrets

```bash
cd functions
firebase functions:secrets:set DISCORD_PUBLIC_KEY
firebase functions:secrets:set DISCORD_DEPLOY_CHANNEL_ID
firebase functions:secrets:set DISCORD_DEPLOY_ALLOWED_ROLE_IDS
firebase functions:secrets:set DISCORD_DEPLOY_ALLOWED_USER_IDS
firebase functions:secrets:set GITHUB_ACTIONS_DISPATCH_TOKEN
firebase deploy --only functions
```

관련 배경은 `functions/README.md`를 따른다.

## 4. Self-check checklist

secret 값을 출력하지 말고 **존재 여부와 workflow 계약**만 확인한다.

### GitHub secret names 확인

```bash
gh secret list
```

최소 확인 대상:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
- `GOOGLE_SERVICES_JSON`
- `DISCORD_BOT_TOKEN`
- `DISCORD_DEPLOY_CHANNEL_ID`

### Workflow contract 확인

```bash
rg -n 'GOOGLE_SERVICES_JSON|DISCORD_BOT_TOKEN|DISCORD_DEPLOY_CHANNEL_ID|GOOGLE_PLAY_SERVICE_ACCOUNT_JSON' .github/workflows
```

기대 결과:
- Android CI / Release QA는 `GOOGLE_SERVICES_JSON`을 dev+prod 둘 다에 복원한다.
- Release Build / Play Deploy는 `GOOGLE_SERVICES_JSON`을 prod만 복원한다.
- Play Deploy만 `DISCORD_BOT_TOKEN`, `DISCORD_DEPLOY_CHANNEL_ID`를 직접 사용한다.
- Version Guard와 Play Deploy가 `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`을 사용한다.

### Helper scope 확인

```bash
bash -n scripts/setup-play-deploy-secrets.sh
bash -n scripts/setup-discord-deploy-secrets.sh
python3 -m unittest scripts.tests.test_setup_deploy_secret_helpers -v
python3 -m unittest scripts.tests.test_play_deploy_secret_contract_runbook -v
```

기대 결과:
- Play helper는 Android/Play/Firebase config secret만 설정한다.
- Discord helper는 GitHub Actions Discord deploy secret만 설정한다.
- helper scope 차이가 테스트로 고정된다.
- runbook의 workflow/ownership matrix가 실제 workflow/Functions README와 어긋나면 테스트가 바로 실패한다.

## 5. Operator pitfalls

1. `GOOGLE_SERVICES_JSON`을 prod-only 의미로 저장해 두고, Android CI / Release QA에서 dev flavor 복원을 잊기.
2. `DISCORD_DEPLOY_CHANNEL_ID`를 GitHub secret에만 넣고 Firebase Functions secret은 빼먹기.
3. `scripts/setup-play-deploy-secrets.sh` 하나로 Discord deploy approval까지 전부 끝났다고 오해하기.
4. 값이 있는지 확인하려고 secret 내용을 출력하거나 repo에 커밋하기.
5. Functions secret을 바꾼 뒤 `firebase deploy --only functions`를 하지 않아 이전 secret version을 계속 쓰기.

## 6. 현재 문서 경계

이 문서는 helper/workflow/function 경계를 먼저 고정하기 위한 runbook이다.
`docs/PLAY_DEPLOYMENT.md` 같은 operator-facing canonical release 문서와 내용이 겹치더라도, 실제 open PR overlap이 정리되기 전까지는 여기 내용을 우선 기준으로 사용해 secret drift를 줄인다.

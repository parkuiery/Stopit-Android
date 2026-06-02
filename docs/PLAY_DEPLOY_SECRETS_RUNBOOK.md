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
| `ANDROID_KEYSTORE_BASE64` | `scripts/setup-play-deploy-secrets.sh` | Release Build, Play Deploy non-production build/upload | runner temp keystore 파일 |
| `ANDROID_KEYSTORE_PASSWORD` | `scripts/setup-play-deploy-secrets.sh` | Release Build, Play Deploy non-production build/upload | Gradle signing env |
| `ANDROID_KEY_ALIAS` | `scripts/setup-play-deploy-secrets.sh` | Release Build, Play Deploy non-production build/upload | Gradle signing env |
| `ANDROID_KEY_PASSWORD` | `scripts/setup-play-deploy-secrets.sh` | Release Build, Play Deploy non-production build/upload | Gradle signing env |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | `scripts/setup-play-deploy-secrets.sh` | Version Guard, Play Deploy non-production build/upload, Play Deploy production promotion | runner temp service-account JSON |
| `GOOGLE_SERVICES_JSON` | `scripts/setup-play-deploy-secrets.sh` | Android CI, Release QA, Release Build, Play Deploy non-production build/upload | 아래 workflow matrix 참고 |
| `DISCORD_BOT_TOKEN` | `scripts/setup-discord-deploy-secrets.sh` 또는 `gh secret set` | Play Deploy | `scripts/notify-discord-deploy.py`가 Discord 메시지 POST에 사용 |
| `DISCORD_DEPLOY_CHANNEL_ID` | `scripts/setup-discord-deploy-secrets.sh` 또는 `gh secret set` | Play Deploy, Firebase Functions | `scripts/notify-discord-deploy.py`의 deploy 알림 채널 + `functions/src/index.ts`의 Discord interaction 채널 검증 |

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
| `play-deploy.yml` non-production build/upload (`internal`, `alpha`, `beta`) | `app/src/prod/google-services.json`만 복원 | `ANDROID_*`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `DISCORD_BOT_TOKEN`, `DISCORD_DEPLOY_CHANNEL_ID` |
| `play-deploy.yml` production promotion | 사용 안 함 | `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` + SemVer tag / `versionCode` governance. `ANDROID_*`와 `GOOGLE_SERVICES_JSON`는 필요 없음 |
| `version-guard.yml` | 사용 안 함 | `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` |
| `functions/README.md`에 설명된 Discord promotion flow | GitHub secret 아님 | Firebase Functions secrets 사용 |

해석 규칙:
- `GOOGLE_SERVICES_JSON`을 “prod 전용 secret”으로 이해하면 Android CI / Release QA 실패 원인을 놓치기 쉽다.
- 더 정확한 계약은 **하나의 secret 값을 workflow마다 다른 source set에 복원한다**이다.
- 현재 steady-state에서는 Android CI / Release QA가 dev+prod 둘 다를 요구하고, Release Build / Play Deploy non-production build/upload는 prod만 요구한다.
- Play Deploy production promotion은 이미 internal track에 올라간 같은 SemVer tag의 `versionCode`를 production으로 승격하는 경로다. 이 경로는 AAB를 다시 빌드/서명하지 않으므로 `ANDROID_*`와 `GOOGLE_SERVICES_JSON`를 복원하지 않으며, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`와 tag/versionCode governance만 필요하다.
- dev/prod `applicationId`를 분리하거나 분리하지 않는 결정을 바꿀 때는 `docs/FLAVOR_APPLICATION_ID_CONTRACT.md`를 먼저 확인한다. `dev` package가 `com.uiery.keep.dev`로 바뀌면 dev Firebase client도 그 package를 포함해야 하고, Release Build / Play Deploy는 계속 production package `com.uiery.keep`와 prod-only restore 경로를 사용해야 한다.

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

적용 범위:
- Release Build와 Play Deploy non-production build/upload(`internal`, `alpha`, `beta`)에는 위 build-upload secret 묶음이 필요하다.
- Play Deploy production promotion은 이 helper가 설정하는 `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`만 사용한다. Android keystore와 `GOOGLE_SERVICES_JSON` 누락은 production promotion 실패 원인이 아니다.

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

### 빠른 자동 self-check

```bash
scripts/check-play-deploy-secret-contract.sh
```

보조 옵션:
- `STOPIT_SKIP_GH_SECRET_LIST=1 scripts/check-play-deploy-secret-contract.sh`
  - `gh` 인증이 없거나 로컬에서 secret 이름 조회만 건너뛰고 workflow/helper/consumer 계약 점검만 하고 싶을 때 사용한다.

이 스크립트가 확인하는 것:
- GitHub repo secret 이름 최소 세트 존재 여부
- workflow / Discord notifier / Firebase Functions consumer grep audit
- `setup-play` / `setup-discord` helper syntax check
- helper scope / runbook contract unittest 실행

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

### Workflow / consumer contract 확인

```bash
rg -n 'GOOGLE_SERVICES_JSON|DISCORD_BOT_TOKEN|DISCORD_DEPLOY_CHANNEL_ID|GOOGLE_PLAY_SERVICE_ACCOUNT_JSON' .github/workflows
rg -n 'DISCORD_BOT_TOKEN|DISCORD_DEPLOY_CHANNEL_ID' scripts/notify-discord-deploy.py
rg -n 'DISCORD_PUBLIC_KEY|DISCORD_DEPLOY_CHANNEL_ID|DISCORD_DEPLOY_ALLOWED_ROLE_IDS|DISCORD_DEPLOY_ALLOWED_USER_IDS|GITHUB_ACTIONS_DISPATCH_TOKEN' functions/src/index.ts
```

기대 결과:
- Android CI / Release QA는 `GOOGLE_SERVICES_JSON`을 dev+prod 둘 다에 복원한다.
- Release Build / Play Deploy non-production build/upload는 `GOOGLE_SERVICES_JSON`을 prod만 복원한다.
- Play Deploy production promotion은 `GOOGLE_SERVICES_JSON`과 `ANDROID_*`를 복원하지 않고, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`로 selected tag의 matching internal release를 승격한다.
- Play Deploy workflow는 `DISCORD_BOT_TOKEN`, `DISCORD_DEPLOY_CHANNEL_ID`를 `scripts/notify-discord-deploy.py`에만 전달한다.
- Firebase Functions는 `functions/src/index.ts`에서 `DISCORD_PUBLIC_KEY`, `DISCORD_DEPLOY_CHANNEL_ID`, `DISCORD_DEPLOY_ALLOWED_ROLE_IDS`, `DISCORD_DEPLOY_ALLOWED_USER_IDS`, `GITHUB_ACTIONS_DISPATCH_TOKEN`을 별도 secret으로 정의한다.
- Version Guard와 Play Deploy가 `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`을 사용한다.

### Helper scope 확인

```bash
scripts/check-play-deploy-secret-contract.sh
```

또는 수동으로는:

```bash
bash -n scripts/setup-play-deploy-secrets.sh
bash -n scripts/setup-discord-deploy-secrets.sh
python3 -m unittest scripts.tests.test_setup_deploy_secret_helpers -v
python3 -m unittest scripts.tests.test_play_deploy_secret_contract_runbook -v
python3 -m unittest scripts.tests.test_check_play_deploy_secret_contract -v
```

기대 결과:
- Play helper는 Android/Play/Firebase config secret만 설정한다.
- Discord helper는 GitHub Actions Discord deploy secret만 설정한다.
- helper scope 차이가 테스트로 고정된다.
- runbook의 workflow/ownership matrix가 실제 workflow/Functions README와 어긋나면 테스트가 바로 실패한다.
- 자동 self-check 스크립트가 필수 secret 이름 누락과 helper/test 계약 이탈을 한 번에 실패로 올린다.

## 5. Operator pitfalls

1. `GOOGLE_SERVICES_JSON`을 prod-only 의미로 저장해 두고, Android CI / Release QA에서 dev flavor 복원을 잊기.
2. `DISCORD_DEPLOY_CHANNEL_ID`를 GitHub secret에만 넣고 Firebase Functions secret은 빼먹기.
3. `scripts/setup-play-deploy-secrets.sh` 하나로 Discord deploy approval까지 전부 끝났다고 오해하기.
4. 값이 있는지 확인하려고 secret 내용을 출력하거나 repo에 커밋하기.
5. Functions secret을 바꾼 뒤 `firebase deploy --only functions`를 하지 않아 이전 secret version을 계속 쓰기.
6. Production promotion 실패를 Android keystore 또는 `GOOGLE_SERVICES_JSON` 누락으로 오진하기. Production promotion은 build/upload가 아니라 기존 internal release 승격이므로 `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, SemVer tag ref, selected tag `versionCode`, Play internal release 존재 여부를 먼저 확인한다.

## 6. Canonical 문서 동기화 상태

이 문서는 helper/workflow/function 경계를 고정하는 source of truth다. Operator-facing canonical release 문서는 이 runbook을 링크하고, 자세한 secret restore/source-set 계약은 여기로 모은다.

현재 canonical 동기화 기준:

1. `docs/PLAY_DEPLOYMENT.md`
   - `scripts/setup-play-deploy-secrets.sh`가 **Android/Play build-upload secrets만** 다룬다고 설명한다.
   - `DISCORD_BOT_TOKEN`, `DISCORD_DEPLOY_CHANNEL_ID`는 `scripts/setup-discord-deploy-secrets.sh` 또는 `gh secret set` 경로로 분리한다.
   - `GOOGLE_SERVICES_JSON` 설명은 `app/src/prod` 전용 문구가 아니라 workflow별 restore matrix를 요약하고, production promotion 예외까지 포함한 자세한 감사는 이 runbook을 따른다.
2. `docs/GIT_WORKFLOW.md`
   - Play deploy secret/setup source of truth가 이 runbook임을 링크한다.
   - Discord deploy notification secret과 Firebase Functions production-promotion secret 경계를 짧게 남긴다.
3. `docs/RELEASE_CHECKLIST.md`
   - release evidence 작성 시 secret setup self-check를 이 runbook 기준으로 참조한다.
   - `GOOGLE_SERVICES_JSON` dev+prod/prod-only/production-promotion-unused 차이는 runbook matrix를 따라가게 한다.
4. `docs/ops/stopit/release-context.md`
   - release guardrail 문맥에서 secret restore/source-set 계약은 이 runbook을 우선 참조한다고 연결한다.

즉, 자세한 계약은 이 runbook에 유지하고, canonical release/operator 문서는 운영자가 이 runbook과 self-check로 빠르게 진입하도록 얇게 연결한다.

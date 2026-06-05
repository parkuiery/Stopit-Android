# Automated Google Play Deployment

Stopit separates CI, release artifact building, and deployment so failures are easy to diagnose.

## Pipeline layers

| Layer | Workflow | Trigger | Does |
| --- | --- | --- | --- |
| CI | `.github/workflows/android-ci.yml` | PR/push to `develop` or `main`, manual | Dev unit tests, dev lint, prod debug APK artifact, and focused runtime smoke on PR/manual runs. No signed release. No Play upload. |
| Release QA | `.github/workflows/release-qa.yml` | `release/* -> main`, `hotfix/* -> main`, manual | Full release JVM/build gate plus focused UI smoke, exact alarm deny/allow instrumentation, and the remaining connected Android suite. |
| Release Build | `.github/workflows/release-build.yml` | `release/* -> main`, `hotfix/* -> main`, or manual dispatch from `main`/`release/*`/`hotfix/*`/SemVer tag refs | Signed `prodRelease` AAB artifact plus `release-provenance.json`. Before the signed AAB is built it runs `:app:lintProdRelease` and `scripts/verify_lint_registry.py` against the prodRelease lint report. The artifact is built with R8 minification and resource shrinking enabled, then the manifest records sha256, size, versionName/versionCode, git ref/SHA, and workflow run URL. Release Build self-verifies that manifest against the generated AAB before artifact upload, using `upload-mode none` and empty Play track/status fields because this layer creates signed evidence but does not upload to Play. Direct push to `main` does not trigger signed artifact generation; release/hotfix PR gates or explicit manual dispatch from an allowed release ref are required. Manual dispatch from feature/docs/automation branches fails before signing secrets are decoded. |
| CD | `.github/workflows/play-deploy.yml` | `v*.*.*` tag, manual | Non-production tracks run `:app:lintProdRelease` + prodRelease lint registry verification before building/signing/uploading the R8/resource-shrunk AAB. They generate `release-provenance.json`, upload it with the signed AAB artifact, and verify the manifest immediately before Google Play upload. Production promotes the already-internal release that matches the selected SemVer tag `versionCode` and does not run `:app:lintProdRelease`, build, or upload a new AAB. |

## What is automated

- Pull requests and pushes to `main`/`develop` run Android CI:
  - `./gradlew :app:testDevDebugUnitTest`
  - `./gradlew :app:lintDevDebug`
  - `./gradlew :app:assembleProdDebug`
  - upload prod debug APK artifact with short 7-day retention
- Android CI `stopit-prod-debug-apk` is a PR/smoke inspection artifact, not a release artifact. Keep its `retention-days: 7` shorter than signed release AAB artifacts (`30` days in Release Build / non-production Play Deploy) so repeated PR runs do not exhaust GitHub Actions artifact storage. The upload step is intentionally `non-blocking` (`continue-on-error: true`): if `Upload prod debug APK` fails with `Artifact storage quota has been hit`, classify it as an external GitHub Actions storage/quota boundary after the preceding build/test steps have passed; delete or let old artifacts expire, wait for GitHub's 6–12 hour quota recalculation window, then rerun the current-head Android CI check to restore the optional artifact.
- Android CI path gating treats `gradlew` / `gradlew.bat`, Gradle config files, and `.github/workflows/android-ci.yml` as **build-critical** root inputs, so wrapper launcher-only PRs still materialize `Fast verification` instead of looking green through skipped checks.
- Pull requests and manual Android CI runs also execute a focused emulator runtime smoke gate:
  - source of truth for this class list is `.github/workflows/android-ci.yml`; release-facing docs should cite the current Android CI run URL instead of copying this PR-gate list into the Release QA evidence section
  - Android CI focused runtime smoke is not the same evidence layer as `Android Release QA / Release instrumentation QA`; release/hotfix readiness uses the exact Release QA list below
  - Android CI dev runtime appops 대상은 `devDebug` 설치 identity인 `com.uiery.keep.dev`다.
  - 자동 검증 범위:
    - `StopitReleaseSmokeTest`: 앱 기동 + Compose navigation host smoke
    - `BackupRestoreRuntimeResetIntegrationTest`: 복원된 Room + 비어 있는 DataStore shape에서 reset-only state가 되살아나지 않는지
    - `HomeAccessibilityPermissionIntegrationTest`: 홈 접근성 권한 경고가 substring 오탐 없이 actual service state와 settings-resume 복귀를 따라 즉시 재동기화되는지
    - `ReceiverRuntimeIntegrationTest`: boot/package-replaced 재수화, 루틴 시작 알림·재예약, notification-denied fallback notice contract
    - `EmergencyUnlockExpiryIntegrationTest`: 긴급해제 만료 state cleanup + 재차단 대상 결정
    - `KeepMessagingServiceIntegrationTest`: stale FCM token overwrite wiring
    - `KeepAccessibilityServiceIntegrationTest`: 실제 AccessibilityService bind 이후 cross-app foreground 차단 진입, emergency unlock 우회, self-uninstall interception safety 계약
  - release/hotfix 전용 exact alarm deny/allow 시나리오와 remaining connected suite는 계속 `Android Release QA`가 담당
- Release candidates targeting `main` also run Android Release QA before merge:
  - `Full release QA`: `./gradlew :app:testDevDebugUnitTest :app:testProdReleaseUnitTest :app:lintProdRelease :app:assembleProdDebug`
  - `Release instrumentation QA`: single-day and multi-day exact-alarm/runtime gates below run on a GitHub-hosted Android emulator.
  - Selector source of truth: `scripts/android_runtime_suites.py` owns selector fragments; workflow YAML owns install/appops sequencing.
  - Suite sequence: `release_focused_ui_smoke` → `release_exact_alarm_default` → `release_exact_alarm_denied` → `release_exact_alarm_allowed` → `release_remaining_runtime` → `notification_denied_receiver` → `notification_denied_emergency_unlock`.
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest`
  - `adb shell cmd appops reset com.uiery.keep.dev` 후 기본 상태 경로를 실행
    - `RoutineExactAlarmPermissionIntegrationTest#defaultExactAlarmAppOpsFollowsAlarmManagerAvailability`
  - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny` 후 아래 focused exact alarm deny 경로를 순서대로 실행
    - `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
    - `RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
    - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
    - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
    - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
  - 위 deny gate는 exact alarm 재예약 실패 시 단일/다중 요일 루틴 receiver 경로가 enabled 루틴을 `enabled=true`로 조용히 남기지 않고, `enabled=false` 강등 + `HAS_SHOWN_ALARM_PERMISSION=false` reset + no pending intent 계약을 지키는지 검증한다.
  - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow` 후 아래 allow/cancel 경로를 실행
    - `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
    - `RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm`
    - `RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.manifest.ManifestContractIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
  - `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
  - `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`

- Release candidates targeting `main` run Android Release Build. Scope: release/* -> main, hotfix/* -> main, or manual dispatch from main/release/*/hotfix/*/SemVer tag refs; direct push to `main` does not trigger a signed release artifact. Manual dispatch from feature/docs/automation branches fails before signing secrets are decoded, so arbitrary refs cannot produce a signed release artifact:
  - `./gradlew :app:testProdReleaseUnitTest`
  - `./gradlew :app:lintProdRelease`
  - `python3 scripts/verify_lint_registry.py --report app/build/reports/lint-results-prodRelease.html ...`
  - signed `prodRelease` AAB build
  - `scripts/release_provenance_manifest.py generate` creates `release-provenance.json` next to the AAB with the AAB `sha256`, file size, `versionName`, `versionCode`, git ref/SHA, and GitHub Actions workflow run URL
  - `scripts/release_provenance_manifest.py verify` self-verifies the Release Build manifest before artifact upload; checksum, size, package, version, or ref drift fails before `Upload signed AAB artifact`
  - upload signed AAB artifact plus `release-provenance.json` to GitHub Actions; the manifest does not include secrets such as keystore contents, service account JSON, Firebase config, or secret names
- Pushing a semver tag like `v1.7.1` or manually dispatching Play Deploy from that tag runs Play deployment only after the Play deploy release guard passes:
  - selected ref must be a SemVer tag
  - tag must be origin/main reachable
  - previous SemVer production completion marker must already exist
  - the guard step must pass `GH_TOKEN` to `scripts/validate-play-deploy-ref.sh`, because that script calls `gh` while checking release/production-marker state in GitHub Actions
  - for non-production tracks (`internal`, `alpha`, `beta`): release unit tests, `:app:lintProdRelease`, prodRelease lint registry verification, signed `prodRelease` AAB build, artifact upload, and Google Play upload run with the Android signing/Firebase build secret bundle
  - non-production Play Deploy generates and verifies `release-provenance.json` before `Upload to Google Play`; operators should use that manifest as the prior non-production signed-AAB evidence for any later production promotion because it ties the internal release to AAB `sha256`, `versionCode`, git SHA/ref, track/status, and workflow run URL
  - non-production staged rollouts are validated before any signing/Firebase/Play secret decode: `release_status=inProgress` requires numeric `rollout_fraction` with `0 < rollout_fraction <= 1`, while `completed`/`draft`/`halted` must leave `rollout_fraction` empty
  - for `production`: the production promotion path validates production staged rollout inputs before `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` is checked or decoded. `release_status=inProgress` requires numeric `rollout_fraction` with `0 < rollout_fraction <= 1`, while `completed`/`draft`/`halted` must leave `rollout_fraction` empty. It does not decode the Android keystore, does not restore `GOOGLE_SERVICES_JSON`, does not run `:app:lintProdRelease`, and does not run `:app:bundleProdRelease`; it requires only `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` plus tag/versionCode governance after input validation, then promotes the matching `internal` release
  - tag-triggered runs upload to Google Play `internal` track by default
- A successful `production` CD run writes two completion markers for the tag only when `track=production` and `release_status=completed`:
  - GitHub Deployment: environment `production`, status `success`
  - GitHub Release note marker: `<!-- stopit-production-deployed: vX.Y.Z -->`
- `draft`, `inProgress`, or `halted` production runs must not write either production completion marker. They may represent a Play Console rollout state, but they are not the repo-side signal that the version fully reached production.
- Manual CD `workflow_dispatch` can upload to `internal`, `alpha`, `beta`, or `production`, but it still requires the same SemVer tag ref release guard as tag-triggered CD.
- Manual CD `workflow_dispatch` still requires the same SemVer tag ref release guard: branch refs are rejected for `internal`, `alpha`, `beta`, and `production`, and the selected tag must be origin/main reachable with the previous SemVer production completion marker present.
- `production` promotion never auto-picks the newest `internal` release. The workflow must run on a SemVer tag ref, resolves that tag's checked-out `app/build.gradle.kts` `versionCode`, and promotes only the matching `internal` release.
- Production promotion does not build or upload a new AAB, so its provenance boundary is the prior non-production `release-provenance.json` generated for the matching internal release. When auditing a production promotion, compare the tag `versionCode` and internal release with the manifest's `versionCode`, AAB `sha256`, git SHA/ref, and workflow run URL instead of expecting a new production AAB manifest.

## Required GitHub secrets

Source of truth: `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md` owns the helper / workflow / Firebase Functions secret boundary. Keep this section as the operator-facing summary, but use that runbook for audits and drift checks.

Set Android / Play build-upload secrets in GitHub repository settings or run `scripts/setup-play-deploy-secrets.sh`. That helper intentionally manages only the build/upload set (`ANDROID_*`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `GOOGLE_SERVICES_JSON`). Discord deploy notification secrets are separate: use `scripts/setup-discord-deploy-secrets.sh` or `gh secret set` for `DISCORD_BOT_TOKEN` and `DISCORD_DEPLOY_CHANNEL_ID`.

| Secret | Used by | Description |
| --- | --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Release Build, Play Deploy non-production build/upload | Base64-encoded Play upload keystore (`.jks` or `.keystore`). Production promotion does not decode or require it. |
| `ANDROID_KEYSTORE_PASSWORD` | Release Build, Play Deploy non-production build/upload | Keystore password. Production promotion does not require it. |
| `ANDROID_KEY_ALIAS` | Release Build, Play Deploy non-production build/upload | Upload key alias. Production promotion does not require it. |
| `ANDROID_KEY_PASSWORD` | Release Build, Play Deploy non-production build/upload | Upload key password. Production promotion does not require it. |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Version Guard, Play Deploy | Google Play Android Publisher service account JSON. `main` 대상 release/hotfix PR의 `Version Guard`가 Google Play visible max `versionCode`를 조회할 때도 필요하다. Production promotion still requires this secret because it calls the Play API to promote the matching internal release. |
| `GOOGLE_SERVICES_JSON` | CI, Release QA, Release Build, Play Deploy non-production build/upload | Prod Firebase `google-services.json` content restored per workflow: Android CI / Release QA write `GOOGLE_SERVICES_JSON_DEV` to `app/src/dev` and `GOOGLE_SERVICES_JSON` to `app/src/prod`, while Release Build / non-production Play Deploy write it only to `app/src/prod`. Production promotion does not restore it. |
| `GOOGLE_SERVICES_JSON_DEV` | CI, Release QA | Dev Firebase `google-services.json` content for `com.uiery.keep.dev`; not used by Release Build / Play Deploy. |
| `DISCORD_BOT_TOKEN` | CD | Discord bot token used by `scripts/notify-discord-deploy.py` to post deploy approval/status messages to the deploy channel. |
| `DISCORD_DEPLOY_CHANNEL_ID` | CD, Firebase Functions | GitHub Actions uses this as the deploy notification channel, and Firebase Functions uses a separate secret of the same name to verify production-promotion interaction channel. Configure both stores when Discord production approval is enabled. |

Operational failure boundary for `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`:
- missing before `main`-target `release/*` or `hotfix/*` PR -> `Version Guard` cannot query Google Play visible max `versionCode`, so the release PR blocks before merge
- missing during non-production tag/manual deploy -> `play-deploy.yml` cannot upload to Google Play after the signed AAB build
- missing during production promotion -> `play-deploy.yml` cannot list/promote the matching internal release; this is the only Play/build secret required by the production promote path

Operational failure boundary for `GOOGLE_SERVICES_JSON`:
- missing during Android CI / Release QA -> the workflow can fail while restoring either `app/src/dev/google-services.json` or `app/src/prod/google-services.json`
- missing during Release Build / non-production Play Deploy -> the workflow fails on prod-only Firebase config restoration before building/uploading the signed artifact
- missing during production promotion -> no effect; production promotion skips Firebase restoration and AAB build

Operational failure boundary for Discord deploy secrets:
- missing GitHub Actions `DISCORD_BOT_TOKEN` / `DISCORD_DEPLOY_CHANNEL_ID` -> Play Deploy can still build/upload, but deploy notification / approval-card posting is skipped or fails depending on the workflow step
- missing Firebase Functions `DISCORD_PUBLIC_KEY`, `DISCORD_DEPLOY_CHANNEL_ID`, allowed role/user IDs, or `GITHUB_ACTIONS_DISPATCH_TOKEN` -> Discord production-promotion button cannot verify/dispatch correctly even if GitHub Actions secrets exist

The service account must have access in Play Console:

1. Play Console → Setup → API access.
2. Link the Google Cloud project if not already linked.
3. Grant the service account app access for Stopit.
4. Required permission: release management/upload permission for the app.

## Secret setup helpers

From the repo root:

```bash
ANDROID_KEYSTORE_PASSWORD='***' \
ANDROID_KEY_PASSWORD='***' \
scripts/setup-play-deploy-secrets.sh \
  --keystore /path/to/upload-key.jks \
  --service-account /path/to/play-service-account.json \
  --alias upload \
  --google-services app/src/prod/google-services.json
```

If the password environment variables are omitted, the script prompts for them.
The script uses `gh secret set` and does not commit secret files.
It does **not** set Discord deploy notification or Firebase Functions promotion secrets.

For GitHub Actions Discord deploy notification secrets:

```bash
DISCORD_BOT_TOKEN='***' \
DISCORD_DEPLOY_CHANNEL_ID='<deploy-channel-id>' \
scripts/setup-discord-deploy-secrets.sh
```

For Firebase Functions production-promotion interaction secrets, use Firebase secret commands as shown below in `Discord production promotion setup`, then redeploy the affected function.

Before a release, verify the contract without printing secret values:

```bash
scripts/check-play-deploy-secret-contract.sh
```

## Release flow

Use the release harness scripts documented in `docs/GIT_WORKFLOW.md`. A new release can start only after the latest existing SemVer tag has reached Google Play `production` and the CD workflow has written its production marker.

```bash
scripts/release-start.sh 1.7.2 --service-account-json /path/to/play-service-account.json
# 또는
scripts/release-start.sh 1.7.2 --fallback-play-max-version-code 23
git push -u origin HEAD
gh pr create --base main --title "release: 1.7.2" --body-file docs/RELEASE_CHECKLIST.md
```

The release PR should pass:

- Branch Hygiene
- Version Guard (must appear on every `main`-target PR; normal `release/*` / `hotfix/*` version PRs and any app/runtime/build-critical release/hotfix PR must prove the candidate `versionCode` is above both `main` and the highest versionCode currently visible through Google Play tracks)
- Workflow-only / governance-only / docs-only main-target hotfixes still keep the `Version Guard` job visible, but the workflow skips the Play service-account restore and versionCode API lookup only after `Classify Version Guard scope` confirms there are no app/runtime/build-critical changes. Changes under `app/**`, `core/**`, Gradle wrapper/root Gradle files, or `gradle/**` require version validation even if `app/build.gradle.kts` was not edited.
- Android CI
- Android Release QA
- Android Release Build
- Receiver/service runtime QA sign-off from `docs/QA_RUNTIME_CHECKLIST.md`
- Backup/restore sign-off from `docs/BACKUP_RESTORE_POLICY.md` when backup XML or persisted-state contracts changed
- If analytics payload / screen contract / queryability assumptions changed, analytics handoff from `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
  - release PR evidence must distinguish **repo 문서/코드 정리 완료** from **GA4 Admin 수동 등록 / metadata 재확인 / 배포 후 14일 재측정**
  - if live metadata still shows only `customUser:routines_count` and the needed `customEvent:*` axes are absent, keep product/metrics conclusions at low confidence instead of claiming queryability is solved

If device/emulator instrumentation could not run, keep the release PR honest: record the exact blocked command (for example the focused exact alarm deny/allow commands or the focused runtime smoke / receiver fallback commands documented in `docs/RELEASE_CHECKLIST.md` and `docs/QA_RUNTIME_CHECKLIST.md`) and attach the manual QA evidence instead of claiming Android runtime verification happened automatically.

If a release includes analytics payload/schema work, also keep the analytics claim honest:

- code/tests/docs being merged does **not** prove GA4 queryability is healthy yet
- before calling a product metric queryable, confirm the required `customEvent:*` axes are actually visible in GA4 metadata and stop treating `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension` as mere no-data
- after the release ships, record the 14-day remeasurement in `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`

After the release PR is merged into `main`:

```bash
git checkout main
git pull origin main
scripts/release-tag.sh 1.7.2
```

The tag push triggers CD and uploads the signed bundle to the Play `internal` track only when `.github/workflows/play-deploy.yml` validates the same safety contract again through `scripts/validate-play-deploy-ref.sh`: the SemVer tag must be reachable from `origin/main`, and `scripts/check-latest-production-deployed.sh` must pass while excluding the just-pushed tag from the "latest existing tag" lookup. This keeps a hand-created `v*.*.*` tag from bypassing the `scripts/release-tag.sh` main/production-marker guardrail.
After a successful internal upload, the CD workflow posts an approval card to the Discord deploy channel. A permitted operator can click **프로덕션 배포** to run the same `play-deploy.yml` workflow on the same SemVer tag with `track=production`.

Manual dispatch tag-governance contract:
- manual `workflow_dispatch` runs are not a branch bypass; they must start from the same SemVer tag ref governance as tag-triggered CD.
- branch refs are rejected for `internal`, `alpha`, `beta`, and `production`, so non-production uploads cannot bypass release PR → merge → tag evidence.

Production promotion safety contract:
- `track=production` runs must start from a SemVer tag ref such as `v1.7.4`; branch refs are rejected.
- `track=production` workflow runs enter the GitHub Environment named `production`; configure that Environment with required reviewer approval in GitHub repository settings. Direct GitHub `workflow_dispatch` and Discord-button dispatches therefore share the same final production approval gate.
- Non-production Play deploys and tag-triggered internal uploads use the non-protected `play-deploy-non-production` environment so internal/alpha/beta evidence can still run without production reviewer approval.
- The production promotion path does not decode the Android keystore, does not restore `GOOGLE_SERVICES_JSON`, and does not run `:app:bundleProdRelease`; it requires only `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` plus tag/versionCode governance before calling the Play promotion helper.
- The workflow reads the checked-out tag's `app/build.gradle.kts`, resolves its `versionCode`, exports `VERSION_CODE`, and passes that to `scripts/promote-google-play-track.js`.
- `scripts/promote-google-play-track.js` fails fast if `DEPLOY_TRACK=production` but `VERSION_CODE` is missing, so the run cannot silently promote the newest `internal` release by accident.
- The promotion log must therefore show the selected tag and the resolved `versionCode`, and Google Play promotion succeeds only when that `versionCode` already exists on the `internal` track.
- Production completion markers are written only when the same run uses `release_status=completed`. If an operator intentionally dispatches production as `draft`, `inProgress`, or `halted`, the run must not create the GitHub Deployment success marker or GitHub Release `stopit-production-deployed` marker that unlocks the next release gate.
- Discord production 알림도 이 경계를 그대로 보여준다. 알림에는 `release_status`가 포함되며, `completed`일 때만 production 완료 marker 작성과 다음 release gate unlock을 안내한다. `draft`, `inProgress`, `halted` 알림은 Google Play rollout/review 상태 확인 대상일 뿐, production 완료 marker나 다음 release gate unlock으로 해석하지 않는다.

## VersionCode guardrail before Play upload

- `scripts/play_version_code_guard.py fetch-play-max` creates a read-only Google Play edit, lists active tracks, and derives the highest visible `versionCode` from every release in those tracks.
- `.github/workflows/version-guard.yml` uses the same script with `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` so `main`-target release/hotfix PRs fail before merge if the candidate `versionCode` is not strictly greater than both `origin/main` and Google Play's visible maximum.
- `scripts/check-release-readiness.sh` and `scripts/bump-version.sh` use the same guard locally. Both commands fetch `origin/main` before reading its `versionCode`; if the fetch fails they stop immediately instead of validating against a stale ref. Provide one of:
  - `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH=/path/to/play-service-account.json`
  - `STOPIT_PLAY_MAX_VERSION_CODE=<known_max>` when live Play API access is unavailable
- The fallback is intentional but should be treated as an operator override. Prefer the live Play API path whenever credentials are available.

The Discord button is handled by the Firebase Function `promoteProductionFromDiscord`, which verifies the Discord interaction signature, channel, and allowed user/role before dispatching GitHub Actions. Keep the Firebase Functions package on the supported Node.js 22 runtime so deploys do not inherit the deprecated Node.js 20 warning. Do not promote an internal release to production until internal QA passes.

## Discord production promotion setup

1. Create or reuse a Discord application/bot and invite it to the server with permission to send messages in the deploy channel.
2. Set GitHub Actions secrets:

```bash
gh secret set DISCORD_BOT_TOKEN
gh secret set DISCORD_DEPLOY_CHANNEL_ID
```

3. Deploy Firebase Functions secrets for the interaction endpoint:

```bash
firebase functions:secrets:set DISCORD_PUBLIC_KEY
firebase functions:secrets:set DISCORD_DEPLOY_CHANNEL_ID
firebase functions:secrets:set DISCORD_DEPLOY_ALLOWED_ROLE_IDS
firebase functions:secrets:set DISCORD_DEPLOY_ALLOWED_USER_IDS
firebase functions:secrets:set GITHUB_ACTIONS_DISPATCH_TOKEN
firebase deploy --only functions:promoteProductionFromDiscord
```

- `DISCORD_PUBLIC_KEY`: Discord application public key.
- `DISCORD_DEPLOY_ALLOWED_ROLE_IDS`: comma-separated role IDs allowed to promote production.
- `DISCORD_DEPLOY_ALLOWED_USER_IDS`: comma-separated user IDs allowed to promote production.
- `GITHUB_ACTIONS_DISPATCH_TOKEN`: fine-grained GitHub token with Actions workflow dispatch permission for this repository.

4. In Discord Developer Portal, set the application's Interactions Endpoint URL to the deployed `promoteProductionFromDiscord` HTTPS URL.

## Local release build check

Without signing environment variables, local release builds fall back to debug signing so normal build checks remain easy:

```bash
./gradlew :app:bundleProdRelease
```

With real signing credentials:

```bash
export ANDROID_KEYSTORE_PATH=/path/to/upload-key.jks
export ANDROID_KEYSTORE_PASSWORD='***'
export ANDROID_KEY_ALIAS='upload'
export ANDROID_KEY_PASSWORD='***'
./gradlew :app:bundleProdRelease
```

Stopit uses `dev` / `prod` flavors in the `app` module, so documentation and local runbooks should prefer explicit commands like `:app:testDevDebugUnitTest` and `:app:assembleProdDebug` over ambiguous shortcuts such as `testDebugUnitTest` or `assembleDebug`.

## Safety notes

- Do not commit keystores, Play service account JSON, or generated AAB/APK files.
- General CI does not upload to Play and does not require Play service account access.
- Release Build creates signed artifacts only; it does not upload externally.
- Tag-triggered CD targets `internal` by default, not production.
- Tag-triggered CD still validates that the tag came from `origin/main` and that the previous SemVer release has the production marker before any Play upload starts.
- Production upload is intentionally manual through workflow dispatch.
- `scripts/release-start.sh` and `scripts/release-tag.sh` block if the latest existing SemVer tag does not have a production completion marker. Use `STOPIT_RELEASE_GATE_BYPASS=1` only for an explicitly approved emergency override.

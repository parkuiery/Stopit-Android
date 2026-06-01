# Stopit Release Context

## 브랜치 전략

Stopit은 `develop`을 일상 개발 기본 브랜치로, `main`을 릴리즈/프로덕션 기준선으로 사용한다.

기본 PR 대상:
- 일반 기능/버그/문서/리팩터링/테스트/CI: `develop`
- release branch: `main`
- hotfix branch: `main`

브랜치 예시:
- `feature/<short-kebab-case>`
- `fix/<short-kebab-case>`
- `refactor/<short-kebab-case>`
- `docs/<short-kebab-case>`
- `test/<short-kebab-case>`
- `ci/<short-kebab-case>`
- `chore/<short-kebab-case>`
- `release/<version>`
- `hotfix/<short-kebab-case>`

## 실행 cron의 기본 PR 규칙

- 작업 전 `git status --short --branch`로 clean 여부를 확인한다.
- dirty tree이면 위험한 stacking을 하지 말고 blocker로 보고한다.
- 한 번에 하나의 작고 안전한 이슈/slice만 처리한다.
- PR base는 일반적으로 `develop`이다.
- PR body는 temp file에 작성하고 `gh pr create --body-file`을 사용한다.
- PR 생성 후 `gh pr view --json body`로 markdown이 깨지지 않았는지 확인한다.
- PR body에는 다음을 포함한다.
  - Summary
  - Verification commands and result
  - Deployment impact
  - `Refs #<issue>` 또는 완전히 충족하면 `Closes #<issue>`

## CI / Release Build / CD 분리

- CI: `.github/workflows/android-ci.yml`
  - PR/push to `develop` or `main`
  - `:app:testDevDebugUnitTest`, `:app:lintDevDebug`, `:app:assembleProdDebug`
  - pull_request / manual runs also execute focused runtime smoke for `StopitReleaseSmokeTest`, `BackupRestoreRuntimeResetIntegrationTest`, the seven focused `ReceiverRuntimeIntegrationTest` methods for boot/package-replaced/time/timezone restore and routine-start reschedule, `EmergencyUnlockExpiryIntegrationTest`, `KeepMessagingServiceIntegrationTest`, and `KeepAccessibilityServiceIntegrationTest` (manual keep -> `BlockActivity`, emergency unlock bypass safety, self-uninstall interception)
  - after the focused class list, CI runs a separate `POST_NOTIFICATION` denied receiver method with host-side `adb shell appops set com.uiery.keep POST_NOTIFICATION ignore`
  - signed release or Play upload 없음

- Ops CI: `.github/workflows/ops-ci.yml`
  - scope: `functions/`, `scripts/promote-google-play-track.js`, `scripts/notify-discord-deploy.py`, `scripts/tests/**`, manual
  - Firebase Functions gate: `npm ci`, `npm run lint`, `npm test` on Node 22
  - release-helper gate: `node --test scripts/tests/test_promote_google_play_track.js`
  - deploy notification gate: `python3 -m py_compile scripts/notify-discord-deploy.py`
  - Android build, signed release artifact, Play upload 없음

- Release QA: `.github/workflows/release-qa.yml`
  - release/hotfix PR to `main` 또는 manual dispatch
  - `pull_request.edited`도 구독한다. `develop → main retarget`만으로 main 대상 PR이 된 경우에도 `Version Guard`, `Android Release QA`, `Android Release Build`가 새 commit 없이 materialize되어야 한다.
  - full release JVM/build gate: `:app:testDevDebugUnitTest`, `:app:testProdReleaseUnitTest`, `:app:lintProdRelease`, `:app:assembleProdDebug`
  - Android 공식 `testing-setup` skill 기준 focused UI smoke: `com.uiery.keep.qa.StopitReleaseSmokeTest`
  - exact alarm denied gate: `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny` 후 아래 focused instrumentation을 순서대로 실행
    - `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
    - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
  - exact alarm allowed gate: `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM allow` 후 `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
  - remaining emulator runtime gate: `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForMyPackageReplaced,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
  - notification-denied receiver gate: `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
  - release PR은 이 check들이 green이 되기 전 merge하지 않는다.

- Release Build: `.github/workflows/release-build.yml`
  - scope: release/* -> main, hotfix/* -> main, manual, or post-merge main push
  - main 대상 `develop → main retarget` PR도 `pull_request.edited`에서 build gate가 materialize되어야 한다.
  - non-release main PR은 signing/Firebase secret 단계 전에 skip되어야 하며 signed release artifact를 만들지 않는다.
  - signed `prodRelease` AAB artifact
  - Play upload 없음

- CD: `.github/workflows/play-deploy.yml`
  - `v*.*.*` tag 또는 manual dispatch
  - signed AAB build and Google Play upload
  - 기본 track은 `internal`

## Play 배포 guardrail

- production 업로드는 자동으로 단정하지 않는다.
- tag-triggered CD는 기본적으로 internal track이다.
- tag-triggered CD도 `scripts/validate-play-deploy-ref.sh`를 먼저 실행해 SemVer tag가 `origin/main`에서 온 release tag인지, 직전 SemVer production marker가 있는지 검증한다. `scripts/release-tag.sh`를 우회해 만든 tag는 Play upload 전에 차단되어야 한다.
- production track은 명시적 판단/수동 workflow dispatch가 필요하다.
- production 승격은 반드시 SemVer tag ref에서만 실행하고, 해당 tag의 `app/build.gradle.kts`에서 읽은 `versionCode`와 일치하는 `internal` release만 승격한다.
- `DEPLOY_TRACK=production`인데 `VERSION_CODE`가 없으면 `scripts/promote-google-play-track.js`가 즉시 실패해야 한다. 최신 internal release 자동 선택은 금지다.
- 실제 배포를 수행하지 않았으면 “배포 완료”라고 말하지 않는다.
- release/hotfix가 main에 들어간 뒤에는 `main -> develop` 역머지를 고려한다.

## Analytics handoff boundary

- Android runtime / release QA가 green이어도 GA4 `customEvent:*` queryability가 green이라는 뜻은 아니다.
- release evidence에는 app/runtime 검증과 analytics queryability 상태를 분리해서 적는다.
- `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`은 no-data보다 **GA4 Admin registration gap**으로 먼저 해석한다.
- release/operator follow-through에서 analytics 수동 경계 판단은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 source of truth로 본다.

## 버전 규칙

- `versionName`: SemVer, 예: `1.7.2`
- `versionCode`: Google Play 단조 증가 정수. 이미 업로드된 값은 재사용 불가.
- `main` 대상 release/hotfix PR은 `versionCode`가 기존 `main`보다 커야 한다.
- tag 형식은 `v{versionName}`이다.

## 검증 명령 원칙

개발 PR:
- focused JVM test: `./gradlew :app:testDevDebugUnitTest --tests '...'`
- 필요 시 broader variant task
- flavorless task는 신중히 사용한다.

릴리즈 준비:
- `scripts/check-release-readiness.sh`
- `./gradlew :app:testDevDebugUnitTest :app:testProdReleaseUnitTest :app:lintProdRelease :app:assembleProdDebug`
- `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM allow && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
- `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForMyPackageReplaced,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
- `./gradlew :app:bundleProdRelease` 또는 dry-run where appropriate
- `main` 대상 PR에서는 `Version Guard`가 항상 생성되어야 한다. 정상적인 release/hotfix PR은 `Android Release QA / Full release QA`, `Android Release QA / Release instrumentation QA`, `Android Release Build`, `Version Guard`, `Branch Hygiene`가 모두 green이어야 merge한다.

CI 확인:
```bash
gh pr checks <PR_NUMBER>
gh pr checks <PR_NUMBER> --watch
```

merge 후 확인:
```bash
gh pr view <PR_NUMBER> --json state,mergedAt,url,mergeCommit
```

## Secret 안전

절대 출력/커밋하지 않는다:
- keystore
- service account JSON private key
- GitHub secrets contents
- Play/Firebase credentials
- generated signed artifacts

## 관련 문서

- `docs/GIT_WORKFLOW.md`
- `docs/PLAY_DEPLOYMENT.md`
- `.github/workflows/`

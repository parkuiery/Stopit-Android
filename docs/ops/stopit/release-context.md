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
  - `Fast verification` runs static policy unit tests, including `scripts.tests.test_sensitive_logging_policy` and `scripts.tests.test_android_manifest_contract`, so raw `android.util.Log` / 민감 logcat 회귀와 manifest/backup policy drift는 normal PR gate에서 차단되어야 한다.
  - Android CI path gating treats `gradlew` / `gradlew.bat`, root Gradle config files, and `.github/workflows/android-ci.yml` as **build-critical** root inputs, so wrapper-only PRs still materialize `Fast verification` instead of looking green through skipped checks.
  - `Fast verification` gate contract: manual `workflow_dispatch` runs may always force the job, and normal PR/push runs must reach the same job through `classify-changes.outputs.android_ci == 'true'` (operator shorthand: `android_ci=true`).
  - pull_request / manual runs also execute focused runtime smoke for `com.uiery.keep.qa.StopitReleaseSmokeTest`, `com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest`, `com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest`, `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm`, `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForPackageAndClockChangeActions`, `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported`, `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timeChangedRestoresRoutinesFromRoomAndSchedulesAlarm`, `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timezoneChangedRestoresMultiDayRoutinesFromRoomAndSchedulesAlarms` (multi-day timezone restore), `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm`, `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine`, `com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage`, `com.uiery.keep.service.KeepMessagingServiceIntegrationTest`, and `com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`.
  - after the focused class list, CI runs a separate `POST_NOTIFICATION` denied method set with host-side `adb shell appops set com.uiery.keep POST_NOTIFICATION ignore`: `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine` and `com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`.
  - signed release or Play upload 없음

- Ops CI: `.github/workflows/ops-ci.yml`
  - scope: `functions/`, `scripts/promote-google-play-track.js`, `scripts/notify-discord-deploy.py`, release-helper guardrail scripts (`scripts/check-release-readiness.sh`, `scripts/check-latest-production-deployed.sh`, `scripts/release-start.sh`, `scripts/bump-version.sh`, `scripts/validate-play-deploy-ref.sh`, `scripts/play_version_code_guard.py`), `scripts/tests/**`, `.github/workflows/**`, `docs/**`, `**/*.md`, manual
  - Workflow syntax lint gate: every `.github/workflows/**` PR/push runs `actionlint` before release/helper work can look green
  - Docs/runbook contract tests gate: docs-only PR/push materializes a lightweight contract job for `scripts.tests.test_play_deploy_secret_contract_runbook`, `scripts.tests.test_release_build_workflow_scope`, `scripts.tests.test_release_qa_runtime_gate_docs`, `scripts.tests.test_android_ci_runtime_smoke_docs`, `scripts.tests.test_release_guard_hotfix_sync`, `scripts.tests.test_ops_ci_workflow`, and `scripts.tests.test_actionlint_gate`; this job intentionally avoids `npm ci`, Gradle, and emulator work
  - Firebase Functions gate: `npm ci`, `npm run lint`, `npm test` on Node 22
  - release-helper gate: `node --test scripts/tests/test_promote_google_play_track.js`
  - release-helper guardrail gate: `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` plus `bash -n` on release helper shell scripts; this discover set includes `scripts.tests.test_android_manifest_contract` for manifest/backup static policy drift.
  - deploy notification gate: `python3 -m py_compile scripts/notify-discord-deploy.py`
  - Android build, signed release artifact, Play upload 없음

- Release QA: `.github/workflows/release-qa.yml`
  - release/hotfix PR to `main` 또는 manual dispatch
  - `pull_request.edited`도 구독한다. `develop → main retarget`만으로 main 대상 PR이 된 경우에도 `Version Guard`, `Android Release QA`, `Android Release Build`가 새 commit 없이 materialize되어야 한다.
  - full release JVM/build gate: `:app:testDevDebugUnitTest`, `:app:testProdReleaseUnitTest`, `:app:lintProdRelease`, `:app:assembleProdDebug`
  - full release QA also runs the same static policy unit-test bundle, including `scripts.tests.test_sensitive_logging_policy` and `scripts.tests.test_android_manifest_contract`, before release JVM/build work. The manifest contract fixes sensitive permissions, component exported flags, AccessibilityService binding/metadata, and backup/data-extraction XML include scope before emulator/runtime checks start.
  - Android 공식 `testing-setup` skill 기준 focused UI smoke: `com.uiery.keep.qa.StopitReleaseSmokeTest`
  - exact alarm denied gate: `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny` 후 아래 focused instrumentation을 순서대로 실행
    - `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
    - `RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
    - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
    - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
    - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
  - exact alarm allowed gate: `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM allow` 후 아래 focused instrumentation을 실행
    - `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
    - `RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm`
    - `RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent`
  - remaining emulator runtime gate: `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForMyPackageReplaced,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.manifest.ManifestContractIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
  - notification-denied receiver gate: `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
  - notification-denied emergency-unlock gate: `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`
  - release PR은 이 check들이 green이 되기 전 merge하지 않는다.

- Release Build: `.github/workflows/release-build.yml`
  - scope: release/* -> main, hotfix/* -> main, or manual dispatch from main/release/*/hotfix/*/SemVer tag refs
  - direct push to `main` does not trigger signed release artifact generation; use release/hotfix PR gates or explicit manual dispatch from an allowed release ref instead.
  - main 대상 `develop → main retarget` PR도 `pull_request.edited`에서 build gate가 materialize되어야 한다.
  - non-release main PR은 signing/Firebase secret 단계 전에 skip되어야 하며 signed release artifact를 만들지 않는다.
  - manual dispatch from feature/docs/automation branches fails before signing secrets are decoded, so arbitrary refs cannot produce a signed release artifact.
  - signed AAB artifact 생성 전에 `:app:lintProdRelease`와 `scripts/verify_lint_registry.py`로 prodRelease lint registry를 재검증한다.
  - signed `prodRelease` AAB artifact
  - Play upload 없음

- CD: `.github/workflows/play-deploy.yml`
  - `v*.*.*` tag 또는 manual dispatch
  - non-production tracks run `:app:lintProdRelease` + prodRelease lint registry verification before build/sign/upload of the signed AAB; production promotes the already-internal release matching the selected SemVer tag `versionCode` and does not run `:app:lintProdRelease`
  - 기본 track은 `internal`

## Play 배포 guardrail

- production 업로드는 자동으로 단정하지 않는다.
- tag-triggered CD는 기본적으로 internal track이다.
- tag-triggered CD도 `scripts/validate-play-deploy-ref.sh`를 먼저 실행해 SemVer tag가 `origin/main`에서 온 release tag인지, 직전 SemVer production marker가 있는지 검증한다. `scripts/release-tag.sh`를 우회해 만든 tag는 Play upload 전에 차단되어야 한다.
- production track은 명시적 판단/수동 workflow dispatch가 필요하다.
- production track dispatch는 `.github/workflows/play-deploy.yml`의 GitHub Environment `production`으로 들어가며, repository settings에서 required reviewer approval을 설정해야 한다. Discord 버튼 경로와 GitHub 직접 dispatch는 같은 Environment 승인 gate를 공유한다.
- manual dispatch도 SemVer tag ref에서만 허용한다. branch ref는 internal/alpha/beta/production 모두 거부하고, 선택한 tag는 tag-triggered CD와 같은 `origin/main` ancestry + 직전 SemVer production marker guard를 통과해야 한다.
- production 승격은 반드시 SemVer tag ref에서만 실행하고, 해당 tag의 `app/build.gradle.kts`에서 읽은 `versionCode`와 일치하는 `internal` release만 승격한다.
- production 승격 경로는 Android keystore decode, `GOOGLE_SERVICES_JSON` 복원, `:app:bundleProdRelease` 실행을 건너뛰고 `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` + tag/versionCode governance만 요구한다.
- `DEPLOY_TRACK=production`인데 `VERSION_CODE`가 없으면 `scripts/promote-google-play-track.js`가 즉시 실패해야 한다. 최신 internal release 자동 선택은 금지다.
- production 완료 marker는 `track=production` + `release_status=completed` 성공 run에서만 기록한다. `draft`, `inProgress`, `halted` production dispatch는 Play rollout 상태일 수 있지만 다음 release gate를 여는 completion marker를 쓰면 안 된다.
- 실제 배포를 수행하지 않았으면 “배포 완료”라고 말하지 않는다.
- release/hotfix가 main에 들어간 뒤에는 `main -> develop` 역머지를 고려한다.

## Analytics handoff boundary

- Android runtime / release QA가 green이어도 GA4 `customEvent:*` queryability가 green이라는 뜻은 아니다.
- release evidence에는 app/runtime 검증과 analytics queryability 상태를 분리해서 적는다.
- `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`은 no-data보다 **GA4 Admin registration gap**으로 먼저 해석한다.
- release/operator follow-through에서 analytics 수동 경계 판단은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 source of truth로 본다.

## Crashlytics #101 post-release recurrence evidence

- #101 Crashlytics fatal/ANR fixes가 release 후보에 포함되면, release PR은 `docs/QA_RUNTIME_CHECKLIST.md#101-release-후-crashlytics-recurrence-evidence-template`를 링크하고 코드 방어 evidence와 live Crashlytics recurrence evidence를 분리해서 기록한다.
- 현재 #101 release follow-through에 포함된 대표 repo-internal fixes는 PR #143, PR #304, PR #320, PR #322이며, 이후 같은 이슈의 추가 PR도 이 묶음에 추가한다.
- release 후 확인할 대표 issue ID에는 fatal `d1369c1905b65f09a031309198552d10`와 startup ANR / background SDK fatal issue IDs가 포함된다.
- #101은 해당 fix가 포함된 release/tag가 실제 배포되고, Firebase Console / Crashlytics MCP / Discord alert payload 기준으로 동일 fatal/ANR issue IDs의 새 버전 재발 여부를 확인한 뒤에만 closure 판단한다.

## 버전 규칙

- `versionName`: SemVer, 예: `1.7.2`
- `versionCode`: Google Play 단조 증가 정수. 이미 업로드된 값은 재사용 불가.
- `main` 대상 release/hotfix PR은 `versionCode`가 기존 `main`보다 커야 한다.
- `main` 대상 release/hotfix PR이 app/runtime/build-critical paths(`app/**`, `core/**`, Gradle wrapper/root Gradle files, `gradle/**`)를 변경하면 `app/build.gradle.kts`가 직접 바뀌지 않았더라도 `Version Guard`가 Play/main max versionCode 검증을 실행해야 한다. workflow/governance/docs-only hotfix만 visible `Version Guard` job 안에서 API validation skip이 허용된다.
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
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM allow && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm,com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm,com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent`
- `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForMyPackageReplaced,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.manifest.ManifestContractIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`
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

Play deploy / release-secret 작업에서는 `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`를 우선 source of truth로 본다. `scripts/setup-play-deploy-secrets.sh`는 Android/Play build-upload secret만 설정하고, Discord deploy notification은 `scripts/setup-discord-deploy-secrets.sh` 또는 `gh secret set`, Firebase Functions production-promotion secret은 `firebase functions:secrets:set ...` 경로로 분리한다. `GOOGLE_SERVICES_JSON`도 workflow별 restore matrix(Android CI / Release QA는 dev+prod, Release Build / Play Deploy non-production build/upload는 prod-only, Play Deploy production promotion은 unused)를 그 runbook 기준으로 확인한다. Production promotion 실패를 Firebase config/Android keystore 누락으로 오진하지 말고, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, SemVer tag ref, selected tag `versionCode`, matching internal release 존재 여부를 먼저 확인한다.

## 관련 문서

- `docs/GIT_WORKFLOW.md`
- `docs/PLAY_DEPLOYMENT.md`
- `.github/workflows/`

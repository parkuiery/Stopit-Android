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
- `automation/*`는 PR head 형식이 아니라 로컬 lane/worktree 안정 브랜치 전용 local lane branch다. docs lane의 `automation/stopit-docs-lane`, qa/code/merge/release lane의 `automation/stopit-*-lane`에서 직접 PR을 만들지 말고, 변경 성격에 맞는 `docs/issue-*`, `test/issue-*`, `fix/issue-*`, `feature/issue-*`, `ci/issue-*`, `chore/issue-*`를 새로 만든다.
- shorthand 허용 prefix는 `docs/*`, `test/*`, `fix/*`, `feature/*`, `refactor/*`, `ci/*`, `chore/*`이며 모두 `develop` 대상이다.

## 실행 cron의 기본 PR 규칙

- 작업 전 `git status --short --branch`로 clean 여부를 확인한다.
- dirty tree이면 위험한 stacking을 하지 말고 blocker로 보고한다.
- 한 번에 하나의 작고 안전한 이슈/slice만 처리한다.
- lane stable branch가 `automation/stopit-docs-lane` 같은 `automation/*`여도, PR head는 Branch Hygiene 허용 prefix여야 한다. 예: 문서/런북은 `docs/issue-...`, workflow/운영 정책은 `ci/issue-...`, QA 기준/테스트는 `test/issue-...`, 앱 코드 수정은 `fix/issue-...` 또는 `feature/issue-...`.
- PR base는 일반적으로 `develop`이다.
- PR body는 temp file에 작성하고 `gh pr create --body-file`을 사용한다.
- PR 생성 후 `gh pr view --json body`로 markdown이 깨지지 않았는지 확인한다.
- PR body에는 다음을 포함한다.
  - Summary
  - Verification commands and result
  - Deployment impact
  - `Refs #<issue>` 또는 완전히 충족하면 `Closes #<issue>`

## CI / Release Build / CD 분리

공통 workflow 유지보수 기준: `Android CI`, `Ops CI`, `Play Deploy`, `Release Build`, `Release QA`, `Version Guard`, `Branch Hygiene`는 모두 릴리즈/거버넌스 신호를 만들기 때문에 `actions/checkout` major version을 저장소 표준(v6)으로 정렬한다. 이 기준이 깨지면 코드 문제가 아니라 workflow runtime drift 때문에 Release QA나 Branch Hygiene만 다르게 실패할 수 있으므로, `scripts.tests.test_actionlint_gate`가 회귀를 차단한다. 또한 Gradle을 실행하거나 release artifact/Play 배포 secret을 다루는 `Android CI`, `Release QA`, `Release Build`, `Play Deploy` workflow는 checkout 직후 `Gradle Wrapper`를 `gradle/actions/wrapper-validation@v6`로 검증해야 한다. 이 wrapper-validation 단계는 `Set up Gradle`, signing/Firebase/Play secret 검증·decode보다 앞에 있어야 하며, 같은 `scripts.tests.test_actionlint_gate` contract가 순서 drift를 막는다. `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` 변경 PR은 wrapper-validation 증적과 contract-test green을 운영 증적으로 남긴다.

- CI: `.github/workflows/android-ci.yml`
  - PR/push to `develop` or `main`
  - `:app:testDevDebugUnitTest`, `:app:lintDevDebug`, `:app:assembleProdDebug`
  - `Fast verification` runs KDS module-local checks before Firebase secret restore: `:core:kds:testDebugUnitTest`, `:core:kds:lintDebug`, `:core:kds:assembleDebug`. This keeps `core/**` / design-system PR evidence first-class instead of relying only on app consumer tasks, and lets KDS regressions surface even when app flavor Firebase restore is the next boundary.
  - `Fast verification` runs static policy unit tests, including `scripts.tests.test_sensitive_logging_policy`, `scripts.tests.test_android_manifest_contract`, `scripts.tests.test_compose_icon_button_accessibility`, `scripts.tests.test_locale_string_parity`, and `scripts.tests.test_locale_string_quality_contract`, so raw `android.util.Log` / 민감 logcat 회귀, manifest/backup policy drift, icon-only accessibility/stateDescription drift, shipped locale string parity drift, and high-traffic locale fallback/brand typo drift는 normal PR gate에서 차단되어야 한다.
  - Android CI path gating treats `gradlew` / `gradlew.bat`, root Gradle config files, and `.github/workflows/android-ci.yml` as **build-critical** root inputs, so wrapper-only PRs still materialize `Fast verification` instead of looking green through skipped checks.
  - `Fast verification` gate contract: manual `workflow_dispatch` runs may always force the job, and normal PR/push runs must reach the same job through `classify-changes.outputs.android_ci == 'true'` (operator shorthand: `android_ci=true`).
  - Dependabot PR의 Firebase secret boundary: actor가 `dependabot[bot]`이고 `GOOGLE_SERVICES_JSON_DEV` / `GOOGLE_SERVICES_JSON` repository secret이 비어 있으면 Android CI는 app Gradle verification과 `runtime smoke`를 job summary와 함께 neutral-deferred 처리한다. 일반 PR / 내부 브랜치 / `workflow_dispatch`에서는 같은 누락을 hard fail로 유지하며, Dependabot dependency PR은 리뷰 후 trusted branch 또는 수동 `workflow_dispatch`에서 Firebase secret이 있는 상태로 runtime smoke evidence를 채운다.
  - pull_request / manual runs also execute focused runtime smoke for the current `.github/workflows/android-ci.yml` class list. Android CI runs this through `scripts/android_runtime_suites.py run-android-ci` aggregate diagnostic mode: `android_ci_focused_runtime_smoke` failure does not hide later exact-alarm / notification-denied / channel-disabled suite results, but the job still exits non-zero after the aggregate summary when any selector or before-command failed. The PR gate includes a minimal exact-alarm smoke (`android_ci_exact_alarm_default`, `android_ci_exact_alarm_denied`, `android_ci_exact_alarm_allowed`) while full single-day/multi-day exact-alarm coverage remains release-only. This Android CI PR gate is intentionally separate from `Android Release QA / Release instrumentation QA`; release/hotfix PR evidence should cite the Android CI run URL for this layer and use the exact Release QA list below for main-target release readiness, including the multi-day exact-alarm/runtime gates.
  - after the focused class list, CI runs a separate `POST_NOTIFICATION` denied method set with host-side `adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore`: `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine` and `com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`.
  - notification permission QA의 현재 지원 범위는 minSdk 33 / Android 13+ `POST_NOTIFICATIONS` runtime permission이다. Android 12L 이하 legacy settings round-trip / `settings_opened` onboarding 검증은 historical / out of scope이며, minSdk를 다시 낮출 때만 현재 검증 대상으로 복원한다.
  - `stopit-prod-debug-apk` upload is a short-lived, non-blocking smoke artifact with `retention-days: 7`, intentionally shorter than signed release artifacts (`30` days). If Android CI passes build/test work and the optional upload reports `Artifact storage quota has been hit`, classify it as a GitHub Actions artifact storage boundary; wait for cleanup and GitHub's 6–12 hour quota recalculation, then rerun for artifact readback rather than treating the PR as a code regression.
  - signed release or Play upload 없음

- Ops CI: `.github/workflows/ops-ci.yml`
  - scope: `functions/`, `scripts/promote-google-play-track.js`, `scripts/notify-discord-deploy.py`, release-helper guardrail scripts (`scripts/check-release-readiness.sh`, `scripts/check-latest-production-deployed.sh`, `scripts/release-start.sh`, `scripts/bump-version.sh`, `scripts/validate-play-deploy-ref.sh`, `scripts/validate-play-rollout-inputs.js`, `scripts/release-tag.sh`, `scripts/check-play-deploy-secret-contract.sh`, `scripts/check-production-environment-approval.sh`, `scripts/setup-play-deploy-secrets.sh`, `scripts/setup-discord-deploy-secrets.sh`, `scripts/play_version_code_guard.py`, `scripts/release_provenance_manifest.py`, `scripts/verify_lint_registry.py`, `scripts/check_workflow_gradle_tasks.py`), `scripts/tests/**`, `tools/aso-screenshots/**`, `.github/workflows/**`, `docs/**`, `**/*.md`, manual
  - ASO screenshot generator gate: `tools/aso-screenshots/**` 변경은 Android 앱 빌드와 분리된 `ASO screenshots build` job을 materialize한다. 이 job은 `tools/aso-screenshots`에서 `bun install --frozen-lockfile` 후 `bun run build`만 수행하며, Gradle/Firebase signing/Play deploy secret을 다루지 않는다.
  - Workflow syntax lint gate: every `.github/workflows/**` PR/push runs `actionlint` before release/helper work can look green. Ops CI installs the pinned repository version `ACTIONLINT_VERSION=1.7.12` from the matching actionlint GitHub Release asset and verifies the archive against the release checksum file instead of using a mutable upstream `main` installer script. Local release readiness also treats missing or version-mismatched `actionlint` as a blocking preflight failure so local and remote workflow lint evidence cannot silently diverge. When updating actionlint, update `.github/workflows/ops-ci.yml`, this release context, `docs/GIT_WORKFLOW.md`, and `scripts.tests.test_actionlint_gate` together.
  - Docs/runbook contract tests gate: docs-only PR/push and release/CI/CD workflow 변경 PR (`android-ci.yml`, `release-qa.yml`, `release-build.yml`, `play-deploy.yml`, `version-guard.yml`) materialize a lightweight contract job for `scripts.tests.test_play_deploy_secret_contract_runbook`, `scripts.tests.test_release_build_workflow_scope`, `scripts.tests.test_release_qa_workflow_scope`, `scripts.tests.test_release_qa_runtime_gate_docs`, `scripts.tests.test_android_ci_runtime_smoke_docs`, `scripts.tests.test_release_guard_hotfix_sync`, `scripts.tests.test_readme_version_contract`, `scripts.tests.test_release_provenance_workflow_contract`, `scripts.tests.test_acquisition_attribution_docs_contract`, `scripts.tests.test_ga4_custom_dimension_registration_docs`, `scripts.tests.test_monetization_interest_contract`, `scripts.tests.test_signed_aab_lint_gate`, `scripts.tests.test_review_prompt_post_release_followthrough_docs`, `scripts.tests.test_workflow_gradle_task_guard`, `scripts.tests.test_release_gradle_task_contract`, `scripts.tests.test_prod_release_shrinking_contract`, `scripts.tests.test_release_signing_gradle_contract`, `scripts.tests.test_aso_screenshots_ci_contract`, `scripts.tests.test_branch_hygiene_policy`, `scripts.tests.test_ops_ci_workflow`, and `scripts.tests.test_actionlint_gate`; this job intentionally avoids `npm ci`, Gradle, and emulator work. `actionlint-only green`은 workflow syntax만 증명하고, `contract-test green`이 operator docs/runbook/source-of-truth drift와 workflow Gradle task guard drift까지 막는다. README 현재 버전 drift의 경우 `scripts.tests.test_readme_version_contract`가 `README.md` ↔ `app/build.gradle.kts` 동기화, `scripts/bump-version.sh` 업데이트 계약, `scripts/release-tag.sh` tag 직전 mismatch 중단, operator docs의 최종 확인 문구를 함께 고정한다.
  - Firebase Functions gate: `npm ci`, `npm run lint`, `npm test` on Node 22
  - release-helper gate: `node --test scripts/tests/test_promote_google_play_track.js` plus staged rollout validator syntax `node --check scripts/validate-play-rollout-inputs.js`
  - release-helper guardrail gate: `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` plus `bash -n` on release helper shell scripts; this discover set includes `scripts.tests.test_android_manifest_contract` for manifest/backup static policy drift.
  - deploy notification gate: `python3 -m py_compile scripts/notify-discord-deploy.py`
  - release provenance manifest gate: `python3 -m py_compile scripts/release_provenance_manifest.py`
  - Android build, signed release artifact, Play upload 없음

- Release QA: `.github/workflows/release-qa.yml`
  - release/hotfix PR to `main`, or manual dispatch from main/release/*/hotfix/*/SemVer tag refs
  - manual dispatch from feature/docs/automation branches fails before Firebase secret restore or emulator setup. Disallowed refs will not restore Firebase secrets or run release QA, so arbitrary branch QA green cannot be mistaken for release evidence.
  - `pull_request.edited`도 구독한다. `develop → main retarget`만으로 main 대상 PR이 된 경우에도 `Version Guard`, `Android Release QA`, `Android Release Build`가 새 commit 없이 materialize되어야 한다.
  - full release JVM/build gate: `:app:testDevDebugUnitTest`, `:app:testProdReleaseUnitTest`, `:app:lintProdRelease`, `:app:assembleProdDebug`
  - full release QA also runs the same static policy unit-test bundle, including sensitive logging, manifest/backup policy, icon-only accessibility/stateDescription, locale string parity, and locale string quality checks, before release JVM/build work. The manifest contract fixes sensitive permissions, component exported flags, AccessibilityService binding/metadata, and backup/data-extraction XML include scope before emulator/runtime checks start.
  - Release instrumentation selector source of truth is `scripts/android_runtime_suites.py`; workflows own install/appops sequencing, while the manifest owns selector fragments. Android CI PR gate is intentionally separate; use the exact Release QA suite sequence below.
  - suite sequence: `release_focused_ui_smoke` → `release_exact_alarm_default` → `release_exact_alarm_denied` → `release_exact_alarm_allowed` → `release_remaining_runtime` → `notification_denied_receiver` → `notification_denied_emergency_unlock` → `notification_channel_disabled`
  - Android 공식 `testing-setup` skill 기준 focused UI smoke: `com.uiery.keep.qa.StopitReleaseSmokeTest`
  - exact alarm default gate: `adb shell cmd appops reset com.uiery.keep.dev` 후 아래 focused instrumentation을 실행
    - `RoutineExactAlarmPermissionIntegrationTest#defaultExactAlarmAppOpsFollowsAlarmManagerAvailability`
  - exact alarm denied gate: `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny` 후 아래 focused instrumentation을 순서대로 실행
    - `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
    - `RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
    - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
    - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
    - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
  - exact alarm allowed/restore gate: `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow` 후 아래 focused instrumentation을 실행
    - `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
    - `RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm`
    - `RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent`
    - `ReceiverExactAlarmPermissionIntegrationTest#exactAlarmPermissionStateChangedWithPermissionAllowedReschedulesEnabledRoutineFromRoom`
    - `ReceiverExactAlarmPermissionIntegrationTest#exactAlarmPermissionStateChangedWithPermissionAllowedReschedulesEveryRepeatDayAlarm`
  - remaining emulator runtime gate: `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest,com.uiery.keep.database.KeepDatabaseMigrationTest,com.uiery.keep.notification.RoutineStartNotificationTapIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.manifest.ManifestContractIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
  - this remaining runtime gate also covers `KeepDatabaseMigrationTest` for Room v5 migration safety and `RoutineStartNotificationTapIntegrationTest` for routine-start notification return-intent safety. New AndroidTest classes under either `app/src/androidTest/java` or `app/src/androidTest/kotlin` must either be included in a runtime suite or listed in `INTENTIONALLY_EXCLUDED_ANDROID_TEST_CLASSES` with a rationale; `scripts.tests.test_android_runtime_suites_manifest` fails on unclassified classes.
  - notification-denied receiver gate: `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
  - notification-denied emergency-unlock gate: `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`
  - notification-channel-disabled gate: `./gradlew :app:installDevDebug && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.notification.NotificationChannelDisabledIntegrationTest`

  - release PR은 이 check들이 green이 되기 전 merge하지 않는다.

- Release Build: `.github/workflows/release-build.yml`
  - scope: release/* -> main, hotfix/* -> main, or manual dispatch from main/release/*/hotfix/*/SemVer tag refs
  - direct push to `main` does not trigger signed release artifact generation; use release/hotfix PR gates or explicit manual dispatch from an allowed release ref instead.
  - main 대상 `develop → main retarget` PR도 `pull_request.edited`에서 build gate가 materialize되어야 한다.
  - non-release main PR은 signing/Firebase secret 단계 전에 skip되어야 하며 signed release artifact를 만들지 않는다.
  - manual dispatch from feature/docs/automation branches fails before signing secrets are decoded, so arbitrary refs cannot produce a signed release artifact.
  - signed AAB artifact 생성 전에 `:app:lintProdRelease`와 `scripts/verify_lint_registry.py`로 prodRelease lint registry를 재검증한다.
  - signed `prodRelease` AAB artifact와 `release-provenance.json` 생성. Manifest는 AAB `sha256`, size, `versionName`, `versionCode`, git SHA/ref, workflow run URL을 기록하고, keystore / service account JSON / Firebase config 같은 secrets는 기록하지 않는다.
  - Gradle `prodRelease` release artifact tasks never use debug signing fallback: `:app:bundleProdRelease`, `:app:assembleProdRelease`, and related signing/packaging tasks fail unless `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` are all present. Debug smoke tasks such as `:app:assembleProdDebug` remain runnable without release signing secrets.
  - artifact upload 전 `scripts/release_provenance_manifest.py verify`로 Release Build manifest를 self-verify한다. 이 경로는 signed artifact evidence만 만들기 때문에 `upload-mode none`과 빈 Play track/status를 사용하고, checksum/size/package/version/`artifact_name`/git ref/GitHub Actions run metadata mismatch가 있으면 `Upload signed AAB artifact` 전에 실패해야 한다.
  - Play upload 없음

- CD: `.github/workflows/play-deploy.yml`
  - `v*.*.*` tag 또는 manual dispatch
  - non-production tracks run `:app:lintProdRelease` + prodRelease lint registry verification before build/sign/upload of the signed AAB; they generate `release-provenance.json`, self-verify it before `Upload signed AAB artifact`, upload the verified AAB/manifest artifact, upload to Google Play through the SHA-pinned audited `r0adkll/upload-google-play@eb49699984a39f23558439581660aa6f088acfd6` action, and only `track=internal` + `release_status=completed` publishes the verified `release-provenance.json` as a same-tag GitHub Release asset for durable fallback. `alpha`/`beta` non-production runs keep their Actions artifact evidence but must not clobber the production-promotion internal fallback asset.
  - the Play upload action pin is a release-critical provenance boundary, not a routine dependency bump. Refresh it only through a reviewed PR that updates `.github/workflows/play-deploy.yml`, `scripts.tests.test_release_provenance_workflow_contract`, and operator docs together; production promotion uses the repo-owned promotion helper and does not run this upload action.
  - production promotes the already-internal release matching the selected SemVer tag `versionCode` and does not run `:app:lintProdRelease`; production promotion uses the prior non-production manifest as the audit boundary rather than creating a new production AAB/provenance artifact
  - production promotion runs the prior internal provenance gate after staged-rollout input validation and must fail-fast before production secrets if the downloaded internal release artifact or `release-provenance.json` does not match the selected tag `versionCode`, git SHA/ref, AAB `sha256`, and workflow run URL evidence. The prior evidence search covers both automatic tag push artifact runs and allowed manual deploy artifact runs created by `workflow_dispatch` for the same SemVer tag/SHA, but each candidate is verified as `track=internal` + `release_status=completed` before selection; manual `alpha`/`beta`/`production` artifacts are classified as `prior internal track mismatch` and skipped before falling back to durable metadata-only evidence. This prior-artifact gate is cross-run: workflow full-artifact verification uses `scripts/release_provenance_manifest.py verify --prior-run`, so the manifest's prior `github_actions.run_id/run_attempt/run_url` must exist but may differ from the current production promotion run. Release Build and non-production Play Deploy same-run self-verification still require current-run metadata equality.
  - #680/#743/#819/#830/#850 retention/publish/selection/clobber boundary: the prior internal `stopit-prod-release-signed-aab` GitHub Actions artifact is a 30-day evidence surface, not durable release history. If production promotion happens after that window, `play-deploy.yml` first attempts the same-tag GitHub Release `release-provenance.json` fallback and runs metadata-only provenance verification before production secrets; if that durable fallback is missing, operators must rerun internal completed Play Deploy for the same SemVer tag before production promotion. If internal fallback publishing fails after `Upload to Google Play` succeeded, classify it as `post-upload durable internal provenance publish failure` / evidence-publish failure, not as Play upload failure; do not blindly re-upload the same `versionCode`. Before an internal completed rerun overwrites an existing durable fallback asset, the workflow downloads the existing `release-provenance.json` and runs `scripts/release_provenance_manifest.py compare`; package/artifact identity, AAB checksum/size, versionName/versionCode, git SHA/ref/ref_name/ref_type, workflow name, track/status, rollout/upload mode must match, while run_id/run_attempt/run_url must be present but may differ across legitimate same-tag reruns. Mismatch is an evidence-publish failure and must refuse `gh release upload --clobber`. Classify `prior internal track mismatch`, `artifact expired/missing`, `durable fallback missing`, `post-upload durable internal provenance publish failure`, and `provenance mismatch` separately.
  - non-production `release_status=inProgress` dispatch validates `rollout_fraction` before signing/Firebase/Play secret decode; fraction must be numeric and satisfy `0 < rollout_fraction <= 1`, while `completed`/`draft`/`halted` require an empty `rollout_fraction`
  - Play Deploy sets up Node 22 with `actions/setup-node@v5` before release-helper JS execution (`scripts/validate-play-rollout-inputs.js` and `scripts/promote-google-play-track.js`) so deployment uses the same Node runtime that Ops CI validates.
  - production staged rollout dispatch validates the same `release_status` / `rollout_fraction` contract before `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` presence check/decode; invalid input fails before Play API promotion work starts
  - 기본 track은 `internal`

## Play 배포 guardrail

- production 업로드는 자동으로 단정하지 않는다.
- tag-triggered CD는 기본적으로 internal track이다.
- tag-triggered CD도 `scripts/validate-play-deploy-ref.sh`를 먼저 실행해 SemVer tag가 `origin/main`에서 온 release tag인지, 직전 SemVer production marker가 있는지 검증한다. `scripts/release-tag.sh`를 우회해 만든 tag는 Play upload 전에 차단되어야 한다.
- 정상 tag 경로인 `scripts/release-tag.sh <versionName>`는 tag 생성/push 직전에 `README.md`의 `현재 버전` 라인과 `app/build.gradle.kts`의 `versionName/versionCode`가 같은지 다시 확인한다. 이 guard는 #558의 bump-version 동기화만으로는 막지 못했던 #613류 main/tag drift를 release tag 직전에 차단하기 위한 최종 검증이다.
- production track은 명시적 판단/수동 workflow dispatch가 필요하다.
- production track dispatch는 `.github/workflows/play-deploy.yml`의 GitHub Environment `production`으로 들어가며, repository settings에서 required reviewer approval을 설정해야 한다. Discord 버튼 경로와 GitHub 직접 dispatch는 같은 Environment 승인 gate를 공유한다. production workflow는 Play secret 검증/디코드 전에 `scripts/check-production-environment-approval.sh`로 `required_reviewers` protection rule을 다시 확인해 unprotected Environment 승격을 fail-fast 처리한다.
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
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
- `./gradlew :app:installDevDebug && adb shell cmd appops reset com.uiery.keep.dev && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#defaultExactAlarmAppOpsFollowsAlarmManagerAvailability`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm,com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm,com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent`
- `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.manifest.ManifestContractIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
- `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`

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

Play deploy / release-secret 작업에서는 `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`를 우선 source of truth로 본다. `scripts/setup-play-deploy-secrets.sh`는 Android/Play build-upload secret만 설정하고, Discord deploy notification은 `scripts/setup-discord-deploy-secrets.sh` 또는 `gh secret set`, Firebase Functions production-promotion secret은 `firebase functions:secrets:set ...` 경로로 분리한다. `GOOGLE_SERVICES_JSON_DEV` dev restore와 `GOOGLE_SERVICES_JSON` prod/prod-only/production-promotion-unused restore matrix도 그 runbook 기준으로 확인한다(Play Deploy production promotion은 unused). Production promotion 실패를 Firebase config/Android keystore 누락으로 오진하지 말고, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, SemVer tag ref, selected tag `versionCode`, matching internal release 존재 여부를 먼저 확인한다.

## 관련 문서

- `docs/GIT_WORKFLOW.md`
- `docs/PLAY_DEPLOYMENT.md`
- `.github/workflows/`

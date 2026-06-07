# Stopit Release Checklist

Use this as the PR body for `release/* -> main` and `hotfix/* -> main` PRs.

## Release identity

- Version name: `x.y.z`
- Version code: `n`
- Release branch: `release/x.y.z` or `hotfix/<name>`
- Target branch: `main`
- Target Play track after tag push: `internal`

## Required checks

- [ ] Latest existing SemVer tag has a production completion marker (`scripts/check-latest-production-deployed.sh`).
- [ ] `versionName` in `app/build.gradle.kts` matches the intended release version.
- [ ] `README.md 현재 버전 라인` matches `app/build.gradle.kts` `versionName/versionCode`; `scripts/release-tag.sh` re-checks this before tag 생성 전.
- [ ] `versionCode` is greater than the version currently on `main` and greater than the highest versionCode currently visible through Google Play tracks; `Version Guard` and `scripts/play_version_code_guard.py` are the source of truth for this check.
- [ ] If the release/hotfix PR changes app/runtime/build-critical paths (`app/**`, `core/**`, Gradle wrapper/root Gradle files, or `gradle/**`), `Version Guard` must run Play/main max version validation even when `app/build.gradle.kts` was not edited; only workflow/governance/docs-only main-target hotfixes may skip that API validation.
- [ ] Any manual `workflow_dispatch` follow-up still starts from the same SemVer tag ref as the release tag; branch ref uploads are not allowed for `internal`, `alpha`, `beta`, or `production`, and the selected tag must pass the same `origin/main` ancestry + previous production marker guard as tag-push CD.
- [ ] Any `track=production` follow-up is waiting on or has passed the GitHub Environment `production` required reviewer gate; direct GitHub dispatch and Discord-button dispatch must share this same approval boundary.
- [ ] `./gradlew :app:testProdReleaseUnitTest` passes locally or in Android Release Build.
- [ ] `./gradlew :app:lintProdRelease` passes in Android Release Build and non-production Play Deploy build/upload before signed AAB creation.
- [ ] `scripts/verify_lint_registry.py` verifies the prodRelease lint report before signed AAB creation/upload.
- [ ] `./gradlew :app:bundleProdRelease` passes locally or in Android Release Build.
- [ ] `prodRelease` R8 minification and resource shrinking remain enabled (`isMinifyEnabled=true`, `isShrinkResources=true`), and Crashlytics stack-trace context is preserved through `app/proguard-rules.pro`.
- [ ] Branch Hygiene passes on the PR.
- [ ] Version Guard runs and passes on the PR (it should appear on every `main`-target PR, not only when `app/build.gradle.kts` changed).
- [ ] `version-guard.yml` uses the same current `actions/checkout` major version as the repository's other governance/release workflows.
- [ ] If a PR was created against `develop` first and then changed by `develop → main retarget`, the `pull_request.edited` trigger materialized `Version Guard`, `Android Release QA`, and `Android Release Build` on the retargeted head before merge.
- [ ] Android CI passes on the PR.
- [ ] Android Release QA passes on the PR:
  - `Full release QA` runs `:app:testDevDebugUnitTest`, `:app:testProdReleaseUnitTest`, `:app:lintProdRelease`, and `:app:assembleProdDebug`.
  - `Release instrumentation QA` selector source of truth is `scripts/android_runtime_suites.py`; workflow YAML owns install/appops sequencing only.
  - Suite sequence: `release_focused_ui_smoke` → `release_exact_alarm_default` → `release_exact_alarm_denied` → `release_exact_alarm_allowed` → `release_remaining_runtime` → `notification_denied_receiver` → `notification_denied_emergency_unlock`.
  - `Release instrumentation QA` runs single-day and multi-day exact-alarm/runtime gates, in order, on a GitHub-hosted Android emulator:
    1. `com.uiery.keep.qa.StopitReleaseSmokeTest`
    2. `RoutineExactAlarmPermissionIntegrationTest#defaultExactAlarmAppOpsFollowsAlarmManagerAvailability` after `adb shell cmd appops reset com.uiery.keep.dev`
    3. `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt` after `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
    4. `RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt` after `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
    5. `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent` after `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
    6. `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm` after `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
    7. `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent` after `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
    8. `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm` after `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
    9. `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent` after `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
    10. `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm` after `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
    11. `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`, `RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm`, and `RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent` after `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow`
    12. `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.manifest.ManifestContractIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
    13. `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
    14. `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`

- [ ] Android Release Build passes `:app:testProdReleaseUnitTest`, `:app:lintProdRelease`, prodRelease lint registry verification, and produces a signed AAB artifact plus `release-provenance.json`.
- [ ] The Release Build provenance manifest is self-verified before `Upload signed AAB artifact` with `upload-mode none` and empty Play track/status fields, so AAB checksum/size/package/version/ref drift blocks artifact upload.
- [ ] The Release Build / non-production Play Deploy provenance manifest records AAB `sha256`, size, `versionName`, `versionCode`, git SHA/ref, track/status, and workflow run URL, and it does not include secrets such as keystore material, service account JSON, or Firebase config.
- [ ] Non-production Play Deploy build/upload runs the same prod lint/registry gate before Google Play upload, generates/verifies `release-provenance.json` before `Upload to Google Play`, and stores the manifest with the signed AAB artifact; production promotion remains a tag/versionCode promotion-only path and does not run `:app:lintProdRelease`, build, or upload a new AAB.
- [ ] Non-production staged rollout dispatch is validated before secret decode/build: `release_status=inProgress` uses numeric `rollout_fraction` where `0 < rollout_fraction <= 1`, and `completed`/`draft`/`halted` leave `rollout_fraction` empty.
- [ ] Production staged rollout dispatch uses the same `release_status`/`rollout_fraction` contract and fails before `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` validation/decode if the input is invalid.
- [ ] No keystore, service account JSON, or `google-services.json` secret was committed.
- [ ] Play deploy secret/setup contract was reviewed against `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`; if Play deploy, Discord deploy, workflow secret restore, or Firebase Functions promotion wiring changed, attach `scripts/check-play-deploy-secret-contract.sh` evidence.
- [ ] `GOOGLE_SERVICES_JSON` restore expectations are not restated ad hoc in the PR: Android CI / Release QA use the runbook's dev+prod restore matrix, Release Build / Play Deploy non-production build/upload use the prod-only restore path, and Play Deploy production promotion records that `GOOGLE_SERVICES_JSON`/`ANDROID_*` are unused.
- [ ] If dev/prod `applicationId` or package identity changed, `docs/FLAVOR_APPLICATION_ID_CONTRACT.md` was reviewed; release evidence proves `prodRelease` / Play deploy still use production package `com.uiery.keep`, and any dev runtime appops evidence uses the dev package explicitly.
- [ ] Receiver/service runtime QA was completed using `docs/QA_RUNTIME_CHECKLIST.md` and `docs/ANDROID_SKILLS_TESTING_QA.md`; release PR evidence distinguishes Android CI의 focused runtime smoke와 release exact alarm/runtime gate를 separate layers로 기록한다.
- [ ] Automated runtime evidence is explicit in the PR body:
  - Android CI focused runtime smoke (PR/manual) is a separate, lower-risk PR gate; cite the current `.github/workflows/android-ci.yml` run URL instead of copying an old class list into the release PR.
  - Release/hotfix runtime evidence comes from `Android Release QA / Release instrumentation QA` below; use that exact list when documenting main-target release readiness.
  - If Android CI and Release QA differ, record them as different layers rather than treating the Android CI smoke list as the release instrumentation list.
- [ ] Android Release QA exact alarm evidence is explicit in the PR body:
  - `adb shell cmd appops reset com.uiery.keep.dev`
  - `RoutineExactAlarmPermissionIntegrationTest#defaultExactAlarmAppOpsFollowsAlarmManagerAvailability`
  - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
  - `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
  - `RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
  - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
  - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
  - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
  - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
  - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
  - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
  - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow`
  - `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
  - `RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm`
  - `RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent`
  - `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.manifest.ManifestContractIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
  - `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
  - `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`

- [ ] The PR body separates what the Android CI focused runtime smoke already proved (launch smoke / backup-restore runtime reset / home accessibility permission warning resync / receiver rehydration / notification-denied fallback notice / emergency-unlock expiry / FCM token regeneration wiring / AccessibilityService bind + self-uninstall interception safety) from what release-only exact alarm gating proved and from any manual-only evidence still required.
- [ ] Manual-only runtime evidence is explicit in the PR body when still required (for example real cold boot, broader device/OEM-specific Accessibility surfaces, end-to-end emergency-unlock foreground return).
- [ ] If the release contains #101 Crashlytics fatal/ANR fixes, the PR body links `docs/QA_RUNTIME_CHECKLIST.md#101-release-후-crashlytics-recurrence-evidence-template` and records `Crashlytics #101 post-release recurrence evidence` separately from the local JVM/runtime smoke evidence:
  - included fixes: PR #143 / PR #304 / PR #320 / PR #322 or a later #101 PR
  - Crashlytics issue IDs to re-check after deploy, including `d1369c1905b65f09a031309198552d10` and the startup ANR / background SDK fatal issue IDs listed in the runtime QA checklist
  - observation window and source (`Firebase Console`, Crashlytics MCP, or Discord alert payload)
  - release 후 closure decision boundary: #101 remains open until the release is live and the same fatal/ANR issue IDs are checked against the shipped version
- [ ] If backup/restore rules or persisted state contracts changed, `docs/BACKUP_RESTORE_POLICY.md` was reviewed and the relevant QA evidence is attached.
- [ ] If full `:app:connectedDevDebugAndroidTest` did not run, the blocker and the focused instrumentation/manual evidence actually collected are recorded in the PR body before merge.
- [ ] If analytics payload, screen name, event dictionary, or queryability assumptions changed, the PR body links `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` and separates these states explicitly:
  - repo code/docs/tests landed
  - GA4 Admin custom dimension / metric registration still manual or already completed
  - live metadata / runReport reconfirmation status
  - post-release 14-day remeasurement still pending or completed
- [ ] If first-lock activation, first-value feedback, or block-intercept UX changed, the PR body links `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md` and records the post-release #14 measurement window:
  - whether the release/tag includes the activation surface commits being measured (for the current #14 baseline: PR #256 `bce1cda`, PR #279 `5c6331d`, PR #283 `35c13eb`; latest production tag `v1.7.7` does not include them)
  - `first_lock_configured` users / `first_open` users
  - `first_core_action_completed` users / `first_lock_configured` users
  - `app_block_intercepted` users / `first_core_action_completed` users
  - whether #13 GA4 Admin registration still blocks source/app/permission-level breakdowns
- [ ] Analytics/product claims in the PR body do not overstate queryability:
  - `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension` is recorded as a registration gap when applicable, not as simple no-data
  - `customUser:routines_count` visibility alone is not used as proof that activation/review/monetization `customEvent:*` axes are queryable
- [ ] User-facing changes are summarized below.

## Change summary

- 

## Risk / rollback notes

- Rollback path: keep the prior production release active in Play Console; do not promote this internal release if smoke testing fails.
- If `Version Guard` or `scripts/check-release-readiness.sh` reports `versionCode must exceed Google Play used max`, bump `versionCode` before merge/tag instead of waiting for a Play upload failure.

## Post-merge steps

```bash
git checkout main
git pull origin main
scripts/release-tag.sh x.y.z

git checkout develop
git pull origin develop
git merge origin/main
git push origin develop
```

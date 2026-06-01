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
- [ ] `versionCode` is greater than the version currently on `main` and greater than the highest versionCode currently visible through Google Play tracks; `Version Guard` and `scripts/play_version_code_guard.py` are the source of truth for this check.
- [ ] Any manual `workflow_dispatch` follow-up still starts from the same SemVer tag ref as the release tag; branch ref uploads are not allowed for `internal`, `alpha`, `beta`, or `production`.
- [ ] `./gradlew :app:testProdReleaseUnitTest` passes locally or in Android Release Build.
- [ ] `./gradlew :app:bundleProdRelease` passes locally or in Android Release Build.
- [ ] Branch Hygiene passes on the PR.
- [ ] Version Guard runs and passes on the PR (it should appear on every `main`-target PR, not only when `app/build.gradle.kts` changed).
- [ ] `version-guard.yml` uses the same current `actions/checkout` major version as the repository's other governance/release workflows.
- [ ] If a PR was created against `develop` first and then changed by `develop → main retarget`, the `pull_request.edited` trigger materialized `Version Guard`, `Android Release QA`, and `Android Release Build` on the retargeted head before merge.
- [ ] Android CI passes on the PR.
- [ ] Android Release QA passes on the PR:
  - `Full release QA` runs `:app:testDevDebugUnitTest`, `:app:testProdReleaseUnitTest`, `:app:lintProdRelease`, and `:app:assembleProdDebug`.
  - `Release instrumentation QA` runs, in order, on a GitHub-hosted Android emulator:
    1. `com.uiery.keep.qa.StopitReleaseSmokeTest`
    2. `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt` after `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny`
    3. `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent` after `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny`
    4. `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent` after `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny`
    5. `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent` after `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny`
    6. `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm` after `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM allow`
    7. `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForMyPackageReplaced,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
    8. `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
- [ ] Android Release Build passes and produces a signed AAB artifact.
- [ ] No keystore, service account JSON, or `google-services.json` secret was committed.
- [ ] Receiver/service runtime QA was completed using `docs/QA_RUNTIME_CHECKLIST.md` and `docs/ANDROID_SKILLS_TESTING_QA.md`; release PR evidence distinguishes Android CI의 focused runtime smoke와 release exact alarm/runtime gate를 separate layers로 기록한다.
- [ ] Automated runtime evidence is explicit in the PR body:
  - Android CI focused runtime smoke (PR/manual):
  - `com.uiery.keep.qa.StopitReleaseSmokeTest`
  - `com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForMyPackageReplaced`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine` (separate `POST_NOTIFICATION ignore` run)
  - `com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest`
  - `com.uiery.keep.service.KeepMessagingServiceIntegrationTest`
  - `com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest` (AccessibilityService bind 이후 cross-app foreground 차단 진입 + emergency unlock 우회 + self-uninstall interception safety)
- [ ] Android Release QA exact alarm evidence is explicit in the PR body:
  - `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny`
  - `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
  - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
  - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
  - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
  - `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM allow`
  - `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
  - `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForMyPackageReplaced,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
  - `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
- [ ] The PR body separates what the Android CI focused runtime smoke already proved (launch smoke / backup-restore runtime reset / receiver rehydration / notification-denied fallback notice / emergency-unlock expiry / FCM token regeneration wiring / AccessibilityService bind + self-uninstall interception safety) from what release-only exact alarm gating proved and from any manual-only evidence still required.
- [ ] Manual-only runtime evidence is explicit in the PR body when still required (for example real cold boot, broader device/OEM-specific Accessibility surfaces, end-to-end emergency-unlock foreground return).
- [ ] If backup/restore rules or persisted state contracts changed, `docs/BACKUP_RESTORE_POLICY.md` was reviewed and the relevant QA evidence is attached.
- [ ] If full `:app:connectedDevDebugAndroidTest` did not run, the blocker and the focused instrumentation/manual evidence actually collected are recorded in the PR body before merge.
- [ ] If analytics payload, screen name, event dictionary, or queryability assumptions changed, the PR body links `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` and separates these states explicitly:
  - repo code/docs/tests landed
  - GA4 Admin custom dimension / metric registration still manual or already completed
  - live metadata / runReport reconfirmation status
  - post-release 14-day remeasurement still pending or completed
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

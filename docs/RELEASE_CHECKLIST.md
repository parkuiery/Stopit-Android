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
- [ ] `./gradlew testProdReleaseUnitTest` passes locally or in Android Release Build.
- [ ] `./gradlew bundleProdRelease` passes locally or in Android Release Build.
- [ ] Branch Hygiene passes on the PR.
- [ ] Version Guard runs and passes on the PR (it should appear on every `main`-target PR, not only when `app/build.gradle.kts` changed).
- [ ] `version-guard.yml` uses the same current `actions/checkout` major version as the repository's other governance/release workflows.
- [ ] Android CI passes on the PR.
- [ ] Android Release QA passes on the PR:
  - `Full release QA` runs `:app:testDevDebugUnitTest`, `:app:testProdReleaseUnitTest`, `:app:lintProdRelease`, and `:app:assembleProdDebug`.
  - `Release instrumentation QA` runs, in order, on a GitHub-hosted Android emulator:
    1. `com.uiery.keep.qa.StopitReleaseSmokeTest`
    2. `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt` after `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny`
    3. `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm` after `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM allow`
    4. `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest`
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
- [ ] Android Release QA exact alarm evidence is explicit in the PR body:
  - `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny`
  - `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
  - `ReceiverRuntimeIntegrationTest#bootReceiverWithoutExactAlarmPermissionDisablesEnabledRoutineAndLeavesNoPendingIntent`
  - `ReceiverRuntimeIntegrationTest#packageReplacedWithoutExactAlarmPermissionDisablesEnabledRoutineAndLeavesNoPendingIntent`
  - `ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutExactAlarmPermissionDisablesEnabledRoutineAndDoesNotReschedule`
  - `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM allow`
  - `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
  - `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest`
- [ ] The PR body separates what the Android CI focused runtime smoke already proved (launch smoke / backup-restore runtime reset / receiver rehydration / notification-denied fallback notice / emergency-unlock expiry / FCM token regeneration wiring) from what release-only exact alarm gating proved and from any manual-only evidence still required.
- [ ] Manual-only runtime evidence is explicit in the PR body when still required (for example real cold boot, cross-app Accessibility blocking, end-to-end emergency-unlock foreground return).
- [ ] If backup/restore rules or persisted state contracts changed, `docs/BACKUP_RESTORE_POLICY.md` was reviewed and the relevant QA evidence is attached.
- [ ] If full `:app:connectedDevDebugAndroidTest` did not run, the blocker and the focused instrumentation/manual evidence actually collected are recorded in the PR body before merge.
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

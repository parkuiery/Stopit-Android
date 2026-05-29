## Summary

- 

## Type

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [ ] UI/style
- [ ] Test
- [ ] Docs
- [ ] CI/ops
- [ ] Release/hotfix

## Target branch check

- [ ] Feature/fix/refactor/docs/test/ci/chore PR targets `develop`.
- [ ] Release/hotfix PR targets `main`.

## Validation

For normal development PRs:
- [ ] `./gradlew :app:testDevDebugUnitTest`
- [ ] `./gradlew :app:lintDevDebug`
- [ ] `./gradlew :app:assembleProdDebug`
- [ ] Focused runtime smoke baseline (`StopitReleaseSmokeTest`, `BackupRestoreRuntimeResetIntegrationTest`, focused `ReceiverRuntimeIntegrationTest` methods, `EmergencyUnlockExpiryIntegrationTest`, `KeepMessagingServiceIntegrationTest`, `KeepAccessibilityServiceIntegrationTest`) is covered by Android CI or equivalent evidence is linked
- [ ] Separate `POST_NOTIFICATION ignore` receiver fallback run is covered by Android CI or equivalent evidence is linked
- [ ] Android CI passes

For release/hotfix PRs:
- [ ] `./gradlew :app:testProdReleaseUnitTest :app:bundleProdRelease --dry-run`
- [ ] Version Guard passes
- [ ] Android Release QA passes
- [ ] Release instrumentation evidence distinguishes:
  - focused UI smoke `com.uiery.keep.qa.StopitReleaseSmokeTest`
  - exact alarm deny gate: `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
  - exact alarm deny receiver reentry gate: `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`, `#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`, `#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
  - exact alarm allow gate: `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
  - remaining focused runtime suite (`StopitReleaseSmokeTest`, `BackupRestoreRuntimeResetIntegrationTest`, focused `ReceiverRuntimeIntegrationTest` methods, `EmergencyUnlockExpiryIntegrationTest`, `KeepMessagingServiceIntegrationTest`, `KeepAccessibilityServiceIntegrationTest`)
  - separate `POST_NOTIFICATION ignore` receiver fallback run
- [ ] Android Release Build passes and produces signed AAB artifact
- [ ] If analytics payload / screen contract / queryability assumptions changed, the PR body distinguishes repo 문서/코드 정리 완료 from GA4 Admin 수동 등록, metadata 재확인, and 배포 후 14일 재측정

For UI/behavior changes:
- [ ] Manual app smoke test

## Release impact

- [ ] No version bump required
- [ ] Version bumped in `app/build.gradle.kts`
- [ ] Google Play/internal release expected after tag
- [ ] Discord deploy-channel approval card expected after internal deploy
- [ ] Production promotion requires Discord deploy-channel button approval by an allowed role/user

## Notes / screenshots


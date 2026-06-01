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
- [ ] If analytics payload / screen contract / queryability assumptions changed, the PR body links `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` and distinguishes these states explicitly:
  - repo 문서/코드/테스트 정리 완료
  - GA4 Admin `customEvent:*` custom dimension / metric registration 상태 (still manual or already completed)
  - live metadata / runReport 재확인 상태
  - 배포 후 14일 재측정 상태
- [ ] Analytics/product claims in the PR body do not overstate queryability:
  - `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`은 단순 no-data가 아니라 registration gap으로 기록한다
  - `customUser:routines_count` 조회 가능만으로 activation / review / monetization `customEvent:*` 축이 queryable하다고 주장하지 않는다

For UI/behavior changes:
- [ ] Manual app smoke test

## Release impact

- [ ] No version bump required
- [ ] Version bumped in `app/build.gradle.kts`
- [ ] Google Play/internal release expected after tag
- [ ] Discord deploy-channel approval card expected after internal deploy
- [ ] Production promotion requires Discord deploy-channel button approval by an allowed role/user

## Notes / screenshots


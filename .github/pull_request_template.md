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
- [ ] Focused runtime smoke baseline (`StopitReleaseSmokeTest`, `BackupRestoreRuntimeResetIntegrationTest`, `ReceiverRuntimeIntegrationTest`, `EmergencyUnlockExpiryIntegrationTest`, `KeepMessagingServiceIntegrationTest`) is covered by Android CI or equivalent evidence is linked
- [ ] Android CI passes

For release/hotfix PRs:
- [ ] `./gradlew :app:testProdReleaseUnitTest :app:bundleProdRelease --dry-run`
- [ ] Version Guard passes
- [ ] Android Release QA passes
- [ ] Release instrumentation evidence distinguishes:
  - focused UI smoke `com.uiery.keep.qa.StopitReleaseSmokeTest`
  - exact alarm deny gate
  - exact alarm allow gate
  - remaining connected Android suite (`notClass=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest`)
- [ ] Android Release Build passes and produces signed AAB artifact

For UI/behavior changes:
- [ ] Manual app smoke test

## Release impact

- [ ] No version bump required
- [ ] Version bumped in `app/build.gradle.kts`
- [ ] Google Play/internal release expected after tag
- [ ] Discord deploy-channel approval card expected after internal deploy
- [ ] Production promotion requires Discord deploy-channel button approval by an allowed role/user

## Notes / screenshots


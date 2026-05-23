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
- [ ] `./gradlew :app:assembleProdDebug`
- [ ] Android CI passes

For release/hotfix PRs:
- [ ] `./gradlew :app:testProdReleaseUnitTest :app:bundleProdRelease --dry-run`
- [ ] Version Guard passes
- [ ] Android Release Build passes and produces signed AAB artifact

For UI/behavior changes:
- [ ] Manual app smoke test

## Release impact

- [ ] No version bump required
- [ ] Version bumped in `app/build.gradle.kts`
- [ ] Google Play/internal release expected after tag

## Notes / screenshots


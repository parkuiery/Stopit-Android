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

- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew assembleProdDebug`
- [ ] `./gradlew testProdReleaseUnitTest bundleProdRelease --dry-run`
- [ ] Manual app smoke test if UI/behavior changed

## Release impact

- [ ] No version bump required
- [ ] Version bumped in `app/build.gradle.kts`
- [ ] Google Play/internal release expected after tag

## Notes / screenshots


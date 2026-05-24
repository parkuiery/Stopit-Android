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
- [ ] `versionCode` is greater than the version currently on `main` and greater than any version already uploaded to Google Play.
- [ ] `./gradlew testProdReleaseUnitTest` passes locally or in Android Release Build.
- [ ] `./gradlew bundleProdRelease` passes locally or in Android Release Build.
- [ ] Branch Hygiene passes on the PR.
- [ ] Version Guard passes on the PR.
- [ ] Android CI passes on the PR.
- [ ] Android Release QA passes on the PR:
  - `Full release QA` runs `:app:testDevDebugUnitTest`, `:app:testProdReleaseUnitTest`, `:app:lintProdRelease`, and `:app:assembleProdDebug`.
  - `Release instrumentation QA` first runs the Android `testing-setup` skill based focused UI smoke `com.uiery.keep.qa.StopitReleaseSmokeTest`, then runs full `:app:connectedDevDebugAndroidTest` on a GitHub-hosted Android emulator.
- [ ] Android Release Build passes and produces a signed AAB artifact.
- [ ] No keystore, service account JSON, or `google-services.json` secret was committed.
- [ ] Receiver/service runtime QA was completed using `docs/QA_RUNTIME_CHECKLIST.md` and `docs/ANDROID_SKILLS_TESTING_QA.md`; the automated `Release instrumentation QA` check is the default evidence for release PRs.
- [ ] Automated runtime evidence is explicit in the PR body:
  - `com.uiery.keep.qa.StopitReleaseSmokeTest`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest`
  - `com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest`
- [ ] Manual-only runtime evidence is explicit in the PR body when still required (for example real cold boot, cross-app Accessibility blocking, end-to-end emergency-unlock foreground return).
- [ ] If backup/restore rules or persisted state contracts changed, `docs/BACKUP_RESTORE_POLICY.md` was reviewed and the relevant QA evidence is attached.
- [ ] If full `:app:connectedDevDebugAndroidTest` did not run, the blocker and the focused instrumentation/manual evidence actually collected are recorded in the PR body before merge.
- [ ] User-facing changes are summarized below.

## Change summary

- 

## Risk / rollback notes

- Rollback path: keep the prior production release active in Play Console; do not promote this internal release if smoke testing fails.
- If a Play upload fails with `version code ... has already been used`, bump `versionCode` and retry.

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

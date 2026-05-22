# Stopit Release Checklist

Use this as the PR body for `release/* -> main` and `hotfix/* -> main` PRs.

## Release identity

- Version name: `x.y.z`
- Version code: `n`
- Release branch: `release/x.y.z` or `hotfix/<name>`
- Target branch: `main`
- Target Play track after tag push: `internal`

## Required checks

- [ ] `versionName` in `app/build.gradle.kts` matches the intended release version.
- [ ] `versionCode` is greater than the version currently on `main` and greater than any version already uploaded to Google Play.
- [ ] `./gradlew testProdReleaseUnitTest` passes locally or in Android Release Build.
- [ ] `./gradlew bundleProdRelease` passes locally or in Android Release Build.
- [ ] Branch Hygiene passes on the PR.
- [ ] Version Guard passes on the PR.
- [ ] Android CI passes on the PR.
- [ ] Android Release Build passes and produces a signed AAB artifact.
- [ ] No keystore, service account JSON, or `google-services.json` secret was committed.
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

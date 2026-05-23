# Automated Google Play Deployment

Stopit separates CI, release artifact building, and deployment so failures are easy to diagnose.

## Pipeline layers

| Layer | Workflow | Trigger | Does |
| --- | --- | --- | --- |
| CI | `.github/workflows/android-ci.yml` | PR/push to `develop` or `main`, manual | Unit tests and prod debug APK artifact. No signed release. No Play upload. |
| Release Build | `.github/workflows/release-build.yml` | PR/push to `main`, manual | Signed `prodRelease` AAB artifact. No Play upload. |
| CD | `.github/workflows/play-deploy.yml` | `v*.*.*` tag, manual | Signed AAB build and Google Play upload. |

## What is automated

- Pull requests and pushes to `main`/`develop` run Android CI:
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleProdDebug`
  - upload prod debug APK artifact
- Release candidates targeting `main` run Android Release Build:
  - `./gradlew testProdReleaseUnitTest`
  - signed `prodRelease` AAB build
  - upload signed AAB artifact to GitHub Actions
- Pushing a semver tag like `v1.7.1` runs Play deployment:
  - release unit tests
  - signed `prodRelease` AAB build
  - artifact upload to GitHub Actions
  - upload to Google Play `internal` track by default
- Manual CD `workflow_dispatch` can upload to `internal`, `alpha`, `beta`, or `production`.

## Required GitHub secrets

Set these in GitHub repository settings or run `scripts/setup-play-deploy-secrets.sh`.

| Secret | Used by | Description |
| --- | --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Release Build, CD | Base64-encoded Play upload keystore (`.jks` or `.keystore`). |
| `ANDROID_KEYSTORE_PASSWORD` | Release Build, CD | Keystore password. |
| `ANDROID_KEY_ALIAS` | Release Build, CD | Upload key alias. |
| `ANDROID_KEY_PASSWORD` | Release Build, CD | Upload key password. |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | CD | Google Play Android Publisher service account JSON. |
| `GOOGLE_SERVICES_JSON` | CI, Release Build, CD | Production Firebase `google-services.json` content for `app/src/prod`. |

The service account must have access in Play Console:

1. Play Console → Setup → API access.
2. Link the Google Cloud project if not already linked.
3. Grant the service account app access for Stopit.
4. Required permission: release management/upload permission for the app.

## Secret setup helper

From the repo root:

```bash
ANDROID_KEYSTORE_PASSWORD='***' \
ANDROID_KEY_PASSWORD='***' \
scripts/setup-play-deploy-secrets.sh \
  --keystore /path/to/upload-key.jks \
  --service-account /path/to/play-service-account.json \
  --alias upload \
  --google-services app/src/prod/google-services.json
```

If the password environment variables are omitted, the script prompts for them.
The script uses `gh secret set` and does not commit secret files.

## Release flow

Use the release harness scripts documented in `docs/GIT_WORKFLOW.md`:

```bash
scripts/release-start.sh 1.7.2
git push -u origin HEAD
gh pr create --base main --title "release: 1.7.2" --body-file docs/RELEASE_CHECKLIST.md
```

The release PR should pass:

- Branch Hygiene
- Version Guard
- Android CI
- Android Release Build
- Receiver/service runtime QA sign-off from `docs/QA_RUNTIME_CHECKLIST.md`

If device/emulator instrumentation could not run, keep the release PR honest: record the exact blocked command (for example `./gradlew :app:connectedDevDebugAndroidTest`) and attach the manual QA evidence instead of claiming Android runtime verification happened automatically.

After the release PR is merged into `main`:

```bash
git checkout main
git pull origin main
scripts/release-tag.sh 1.7.2
```

The tag push triggers CD and uploads the signed bundle to the Play `internal` track.
Promote from internal to production in Play Console, or manually run the deploy workflow with `track=production` when ready.

## Local release build check

Without signing environment variables, local release builds fall back to debug signing so normal build checks remain easy:

```bash
./gradlew bundleProdRelease
```

With real signing credentials:

```bash
export ANDROID_KEYSTORE_PATH=/path/to/upload-key.jks
export ANDROID_KEYSTORE_PASSWORD='***'
export ANDROID_KEY_ALIAS='upload'
export ANDROID_KEY_PASSWORD='***'
./gradlew bundleProdRelease
```

## Safety notes

- Do not commit keystores, Play service account JSON, or generated AAB/APK files.
- General CI does not upload to Play and does not require Play service account access.
- Release Build creates signed artifacts only; it does not upload externally.
- Tag-triggered CD targets `internal` by default, not production.
- Production upload is intentionally manual through workflow dispatch.

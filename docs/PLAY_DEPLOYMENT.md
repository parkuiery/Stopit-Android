# Automated Google Play Deployment

This project can build a signed production Android App Bundle and upload it to Google Play from GitHub Actions.

## What is automated

- Pull requests and pushes to `main`/`develop` run Android CI:
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleProdDebug`
  - `./gradlew bundleProdRelease` as a release-bundle smoke test
- Pushing a semver tag like `v1.7.1` runs Play deployment:
  - release unit tests
  - signed `prodRelease` AAB build
  - artifact upload to GitHub Actions
  - upload to Google Play `internal` track by default
- Manual `workflow_dispatch` can upload to `internal`, `alpha`, `beta`, or `production`.

## Required GitHub secrets

Set these in GitHub repository settings or run `scripts/setup-play-deploy-secrets.sh`.

| Secret | Description |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded Play upload keystore (`.jks` or `.keystore`). |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password. |
| `ANDROID_KEY_ALIAS` | Upload key alias. |
| `ANDROID_KEY_PASSWORD` | Upload key password. |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Google Play Android Publisher service account JSON. |
| `GOOGLE_SERVICES_JSON` | Production Firebase `google-services.json` content for `app/src/prod`. |

The service account must have access in Play Console:

1. Play Console → Setup → API access.
2. Link the Google Cloud project if not already linked.
3. Grant the service account app access for Stopit.
4. Required permission: release management/upload permission for the app.

## Secret setup helper

From the repo root:

```bash
ANDROID_KEYSTORE_PASSWORD='...' \
ANDROID_KEY_PASSWORD='...' \
scripts/setup-play-deploy-secrets.sh \
  --keystore /path/to/upload-key.jks \
  --service-account /path/to/play-service-account.json \
  --alias upload \
  --google-services app/src/prod/google-services.json
```

If the password environment variables are omitted, the script prompts for them.

The script uses `gh secret set` and does not commit secret files.

## Release flow

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Merge the release commit into `main`.
3. Tag the release:

```bash
git tag v1.7.1
git push origin v1.7.1
```

4. GitHub Actions uploads the signed bundle to the Play `internal` track.
5. Promote from internal to production in Play Console, or manually run the deploy workflow with `track=production` when ready.

## Local release build check

Without signing environment variables, local release builds fall back to debug signing so normal build checks remain easy:

```bash
./gradlew bundleProdRelease
```

With real signing credentials:

```bash
export ANDROID_KEYSTORE_PATH=/path/to/upload-key.jks
export ANDROID_KEYSTORE_PASSWORD='...'
export ANDROID_KEY_ALIAS='upload'
export ANDROID_KEY_PASSWORD='...'
./gradlew bundleProdRelease
```

## Safety notes

- Do not commit keystores, Play service account JSON, or generated AAB/APK files.
- Tag-triggered deployment targets `internal` by default, not production.
- Production upload is intentionally manual through workflow dispatch.

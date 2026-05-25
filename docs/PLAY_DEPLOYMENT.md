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
  - `./gradlew :app:testDevDebugUnitTest`
  - `./gradlew :app:assembleProdDebug`
  - upload prod debug APK artifact
- Release candidates targeting `main` run Android Release Build:
  - `./gradlew :app:testProdReleaseUnitTest`
  - signed `prodRelease` AAB build
  - upload signed AAB artifact to GitHub Actions
- Pushing a semver tag like `v1.7.1` runs Play deployment:
  - release unit tests
  - signed `prodRelease` AAB build
  - artifact upload to GitHub Actions
  - upload to Google Play `internal` track by default
- A successful `production` CD run writes two completion markers for the tag:
  - GitHub Deployment: environment `production`, status `success`
  - GitHub Release note marker: `<!-- stopit-production-deployed: vX.Y.Z -->`
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
| `DISCORD_BOT_TOKEN` | CD | Discord bot token used to post deploy approval cards to the deploy channel. |
| `DISCORD_DEPLOY_CHANNEL_ID` | CD | Discord channel ID for deploy approval/status messages. |

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

Use the release harness scripts documented in `docs/GIT_WORKFLOW.md`. A new release can start only after the latest existing SemVer tag has reached Google Play `production` and the CD workflow has written its production marker.

```bash
scripts/release-start.sh 1.7.2 --service-account-json /path/to/play-service-account.json
# 또는
scripts/release-start.sh 1.7.2 --fallback-play-max-version-code 23
git push -u origin HEAD
gh pr create --base main --title "release: 1.7.2" --body-file docs/RELEASE_CHECKLIST.md
```

The release PR should pass:

- Branch Hygiene
- Version Guard (must appear on every `main`-target PR, even before `app/build.gradle.kts` changes, and must prove the candidate `versionCode` is above both `main` and the highest versionCode currently visible through Google Play tracks)
- Android CI
- Android Release Build
- Receiver/service runtime QA sign-off from `docs/QA_RUNTIME_CHECKLIST.md`
- Backup/restore sign-off from `docs/BACKUP_RESTORE_POLICY.md` when backup XML or persisted-state contracts changed

If device/emulator instrumentation could not run, keep the release PR honest: record the exact blocked command (for example `./gradlew :app:connectedDevDebugAndroidTest`) and attach the manual QA evidence instead of claiming Android runtime verification happened automatically.

After the release PR is merged into `main`:

```bash
git checkout main
git pull origin main
scripts/release-tag.sh 1.7.2
```

The tag push triggers CD and uploads the signed bundle to the Play `internal` track.
After a successful internal upload, the CD workflow posts an approval card to the Discord deploy channel. A permitted operator can click **프로덕션 배포** to run the same `play-deploy.yml` workflow on the same SemVer tag with `track=production`.

## VersionCode guardrail before Play upload

- `scripts/play_version_code_guard.py fetch-play-max` creates a read-only Google Play edit, lists active tracks, and derives the highest visible `versionCode` from every release in those tracks.
- `.github/workflows/version-guard.yml` uses the same script with `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` so `main`-target release/hotfix PRs fail before merge if the candidate `versionCode` is not strictly greater than both `origin/main` and Google Play's visible maximum.
- `scripts/check-release-readiness.sh` and `scripts/bump-version.sh` use the same guard locally. Provide one of:
  - `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH=/path/to/play-service-account.json`
  - `STOPIT_PLAY_MAX_VERSION_CODE=<known_max>` when live Play API access is unavailable
- The fallback is intentional but should be treated as an operator override. Prefer the live Play API path whenever credentials are available.

The Discord button is handled by the Firebase Function `promoteProductionFromDiscord`, which verifies the Discord interaction signature, channel, and allowed user/role before dispatching GitHub Actions. Keep the Firebase Functions package on the supported Node.js 22 runtime so deploys do not inherit the deprecated Node.js 20 warning. Do not promote an internal release to production until internal QA passes.

## Discord production promotion setup

1. Create or reuse a Discord application/bot and invite it to the server with permission to send messages in the deploy channel.
2. Set GitHub Actions secrets:

```bash
gh secret set DISCORD_BOT_TOKEN
gh secret set DISCORD_DEPLOY_CHANNEL_ID
```

3. Deploy Firebase Functions secrets for the interaction endpoint:

```bash
firebase functions:secrets:set DISCORD_PUBLIC_KEY
firebase functions:secrets:set DISCORD_DEPLOY_CHANNEL_ID
firebase functions:secrets:set DISCORD_DEPLOY_ALLOWED_ROLE_IDS
firebase functions:secrets:set DISCORD_DEPLOY_ALLOWED_USER_IDS
firebase functions:secrets:set GITHUB_ACTIONS_DISPATCH_TOKEN
firebase deploy --only functions:promoteProductionFromDiscord
```

- `DISCORD_PUBLIC_KEY`: Discord application public key.
- `DISCORD_DEPLOY_ALLOWED_ROLE_IDS`: comma-separated role IDs allowed to promote production.
- `DISCORD_DEPLOY_ALLOWED_USER_IDS`: comma-separated user IDs allowed to promote production.
- `GITHUB_ACTIONS_DISPATCH_TOKEN`: fine-grained GitHub token with Actions workflow dispatch permission for this repository.

4. In Discord Developer Portal, set the application's Interactions Endpoint URL to the deployed `promoteProductionFromDiscord` HTTPS URL.

## Local release build check

Without signing environment variables, local release builds fall back to debug signing so normal build checks remain easy:

```bash
./gradlew :app:bundleProdRelease
```

With real signing credentials:

```bash
export ANDROID_KEYSTORE_PATH=/path/to/upload-key.jks
export ANDROID_KEYSTORE_PASSWORD='***'
export ANDROID_KEY_ALIAS='upload'
export ANDROID_KEY_PASSWORD='***'
./gradlew :app:bundleProdRelease
```

Stopit uses `dev` / `prod` flavors in the `app` module, so documentation and local runbooks should prefer explicit commands like `:app:testDevDebugUnitTest` and `:app:assembleProdDebug` over ambiguous shortcuts such as `testDebugUnitTest` or `assembleDebug`.

## Safety notes

- Do not commit keystores, Play service account JSON, or generated AAB/APK files.
- General CI does not upload to Play and does not require Play service account access.
- Release Build creates signed artifacts only; it does not upload externally.
- Tag-triggered CD targets `internal` by default, not production.
- Production upload is intentionally manual through workflow dispatch.
- `scripts/release-start.sh` and `scripts/release-tag.sh` block if the latest existing SemVer tag does not have a production completion marker. Use `STOPIT_RELEASE_GATE_BYPASS=1` only for an explicitly approved emergency override.

# Stopit Firebase Functions

Crashlytics alerts are forwarded to Discord through Firebase Functions. The same Functions project also handles Discord deploy-channel button interactions for production promotion.

## Setup

This Functions package targets the Firebase Node.js 22 runtime.

Secret ownership note:
- GitHub Actions repo secrets for Android/Play build/upload and Discord deploy notifications are documented in `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`.
- This `functions/` setup only covers Firebase Functions secrets used by Crashlytics alerts and production-promotion interactions.

```bash
cd functions
npm install
firebase functions:secrets:set DISCORD_WEBHOOK_URL
firebase functions:secrets:set DISCORD_PUBLIC_KEY
firebase functions:secrets:set DISCORD_DEPLOY_CHANNEL_ID
firebase functions:secrets:set DISCORD_DEPLOY_ALLOWED_ROLE_IDS
firebase functions:secrets:set DISCORD_DEPLOY_ALLOWED_USER_IDS
firebase functions:secrets:set GITHUB_ACTIONS_DISPATCH_TOKEN
firebase deploy --only functions
```

## Local verification

```bash
cd functions
npm test
npm run build
```

## Triggered alerts

- New fatal issue
- New non-fatal issue
- New ANR issue
- Regression alert
- Velocity alert

These functions use Firebase Alerts triggers from Crashlytics and post a formatted message to Discord.

## Production promotion interaction

`promoteProductionFromDiscord` receives Discord message-component interactions from the deploy approval card. It:

1. verifies Discord's Ed25519 request signature using `DISCORD_PUBLIC_KEY`;
2. only accepts interactions from `DISCORD_DEPLOY_CHANNEL_ID`;
3. allows only comma-separated `DISCORD_DEPLOY_ALLOWED_ROLE_IDS` or `DISCORD_DEPLOY_ALLOWED_USER_IDS`;
4. dispatches `.github/workflows/play-deploy.yml` on the selected SemVer tag with `track=production`.

The function is an interaction/authentication gate, not the final production execution approval. The dispatched `track=production` workflow enters the GitHub Environment named `production`; configure that Environment in GitHub repository settings with a required reviewer so Discord-button dispatches and direct GitHub `workflow_dispatch` runs share the same final approval boundary.

Set the Discord application's Interactions Endpoint URL to the deployed HTTPS function URL.

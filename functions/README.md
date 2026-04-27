# Stopit Firebase Functions

Crashlytics alerts are forwarded to Discord through Firebase Functions.

## Setup

```bash
cd functions
npm install
firebase functions:secrets:set DISCORD_WEBHOOK_URL
firebase deploy --only functions
```

## Triggered alerts

- New fatal issue
- New non-fatal issue
- New ANR issue
- Regression alert
- Velocity alert

These functions use Firebase Alerts triggers from Crashlytics and post a formatted message to Discord.

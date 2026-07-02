#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/setup-play-deploy-secrets.sh --keystore <upload-key.jks> --service-account <play-service-account.json> --alias <key-alias> [--google-services app/src/prod/google-services.json] [--google-services-dev app/src/dev/google-services.json]

Required environment variables or prompts:
  ANDROID_KEYSTORE_PASSWORD
  ANDROID_KEY_PASSWORD

This script stores required Android/Play deployment credentials as GitHub Actions secrets for the current repo:
  ANDROID_KEYSTORE_BASE64
  ANDROID_KEYSTORE_PASSWORD
  ANDROID_KEY_ALIAS
  ANDROID_KEY_PASSWORD
  GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
  GOOGLE_SERVICES_JSON
  GOOGLE_SERVICES_JSON_DEV

Scope notes:
- configures build/upload secrets only
- does NOT configure Discord deploy notification secrets (`DISCORD_BOT_TOKEN`, `DISCORD_DEPLOY_CHANNEL_ID`)
- use `scripts/setup-discord-deploy-secrets.sh` for GitHub Actions Discord deploy secrets
- see `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md` for the full secret matrix

It does not commit any secret files.
USAGE
}

KEYSTORE=""
SERVICE_ACCOUNT=""
GOOGLE_SERVICES="app/src/prod/google-services.json"
GOOGLE_SERVICES_DEV="app/src/dev/google-services.json"
KEY_ALIAS=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keystore)
      KEYSTORE="${2:-}"
      shift 2
      ;;
    --service-account)
      SERVICE_ACCOUNT="${2:-}"
      shift 2
      ;;
    --google-services)
      GOOGLE_SERVICES="${2:-}"
      shift 2
      ;;
    --google-services-dev)
      GOOGLE_SERVICES_DEV="${2:-}"
      shift 2
      ;;
    --alias)
      KEY_ALIAS="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$KEYSTORE" || -z "$SERVICE_ACCOUNT" || -z "$KEY_ALIAS" ]]; then
  usage >&2
  exit 2
fi

if [[ ! -f "$KEYSTORE" ]]; then
  echo "Keystore file not found: $KEYSTORE" >&2
  exit 1
fi

if [[ ! -f "$SERVICE_ACCOUNT" ]]; then
  echo "Service account JSON not found: $SERVICE_ACCOUNT" >&2
  exit 1
fi

if [[ ! -f "$GOOGLE_SERVICES" ]]; then
  echo "google-services.json file not found: $GOOGLE_SERVICES" >&2
  exit 1
fi

if [[ ! -f "$GOOGLE_SERVICES_DEV" ]]; then
  echo "dev google-services.json file not found: $GOOGLE_SERVICES_DEV" >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required." >&2
  exit 1
fi

gh auth status >/dev/null

if [[ -z "${ANDROID_KEYSTORE_PASSWORD:-}" ]]; then
  read -rsp "Android keystore password: " ANDROID_KEYSTORE_PASSWORD
  echo
fi

if [[ -z "${ANDROID_KEY_PASSWORD:-}" ]]; then
  read -rsp "Android key password: " ANDROID_KEY_PASSWORD
  echo
fi

TMP_KEYSTORE_B64=$(mktemp)
trap 'rm -f "$TMP_KEYSTORE_B64"' EXIT
base64 < "$KEYSTORE" | tr -d '\n' > "$TMP_KEYSTORE_B64"

gh secret set ANDROID_KEYSTORE_BASE64 < "$TMP_KEYSTORE_B64"
printf '%s' "$ANDROID_KEYSTORE_PASSWORD" | gh secret set ANDROID_KEYSTORE_PASSWORD
printf '%s' "$KEY_ALIAS" | gh secret set ANDROID_KEY_ALIAS
printf '%s' "$ANDROID_KEY_PASSWORD" | gh secret set ANDROID_KEY_PASSWORD
gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON < "$SERVICE_ACCOUNT"
gh secret set GOOGLE_SERVICES_JSON < "$GOOGLE_SERVICES"
gh secret set GOOGLE_SERVICES_JSON_DEV < "$GOOGLE_SERVICES_DEV"

echo "Deployment secrets configured for $(gh repo view --json nameWithOwner -q .nameWithOwner)."
echo "Discord deploy notification secrets are managed separately via scripts/setup-discord-deploy-secrets.sh."

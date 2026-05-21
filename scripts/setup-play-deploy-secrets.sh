#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/setup-play-deploy-secrets.sh --keystore <upload-key.jks> --service-account <play-service-account.json> --alias <key-alias>

Required environment variables or prompts:
  ANDROID_KEYSTORE_PASSWORD
  ANDROID_KEY_PASSWORD

This script stores required deployment credentials as GitHub Actions secrets for the current repo:
  ANDROID_KEYSTORE_BASE64
  ANDROID_KEYSTORE_PASSWORD
  ANDROID_KEY_ALIAS
  ANDROID_KEY_PASSWORD
  GOOGLE_PLAY_SERVICE_ACCOUNT_JSON

It does not commit any secret files.
USAGE
}

KEYSTORE=""
SERVICE_ACCOUNT=""
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

echo "Deployment secrets configured for $(gh repo view --json nameWithOwner -q .nameWithOwner)."

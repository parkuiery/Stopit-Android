#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  DISCORD_BOT_TOKEN=<token> DISCORD_DEPLOY_CHANNEL_ID=<channel-id> scripts/setup-discord-deploy-secrets.sh
  scripts/setup-discord-deploy-secrets.sh --channel-id <channel-id>

This script stores GitHub Actions deploy-notification secrets for the current repo:
  DISCORD_BOT_TOKEN
  DISCORD_DEPLOY_CHANNEL_ID

Scope:
- configures GitHub repository secrets used by `.github/workflows/play-deploy.yml`
- does NOT configure Firebase Functions secrets such as DISCORD_PUBLIC_KEY or GITHUB_ACTIONS_DISPATCH_TOKEN
- for the full secret contract, see docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md and functions/README.md
USAGE
}

CHANNEL_ID="${DISCORD_DEPLOY_CHANNEL_ID:-}"
BOT_TOKEN="${DISCORD_BOT_TOKEN:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --channel-id)
      CHANNEL_ID="${2:-}"
      shift 2
      ;;
    --bot-token)
      BOT_TOKEN="${2:-}"
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

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required." >&2
  exit 1
fi

gh auth status >/dev/null

if [[ -z "$BOT_TOKEN" ]]; then
  read -rsp "Discord bot token: " BOT_TOKEN
  echo
fi

if [[ -z "$CHANNEL_ID" ]]; then
  read -rp "Discord deploy channel ID: " CHANNEL_ID
fi

if [[ -z "$BOT_TOKEN" || -z "$CHANNEL_ID" ]]; then
  echo "DISCORD_BOT_TOKEN and DISCORD_DEPLOY_CHANNEL_ID are required." >&2
  exit 2
fi

printf '%s' "$BOT_TOKEN" | gh secret set DISCORD_BOT_TOKEN
printf '%s' "$CHANNEL_ID" | gh secret set DISCORD_DEPLOY_CHANNEL_ID

echo "Discord deploy notification secrets configured for $(gh repo view --json nameWithOwner -q .nameWithOwner)."
echo "Firebase Functions promotion secrets remain a separate step; see functions/README.md and docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md."

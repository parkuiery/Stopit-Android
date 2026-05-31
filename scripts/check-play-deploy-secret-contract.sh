#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "$SCRIPT_DIR/.." && pwd)
cd "$REPO_ROOT"

REQUIRED_GH_SECRETS=(
  ANDROID_KEYSTORE_BASE64
  ANDROID_KEYSTORE_PASSWORD
  ANDROID_KEY_ALIAS
  ANDROID_KEY_PASSWORD
  GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
  GOOGLE_SERVICES_JSON
  DISCORD_BOT_TOKEN
  DISCORD_DEPLOY_CHANNEL_ID
)

check_github_secret_names() {
  if [[ "${STOPIT_SKIP_GH_SECRET_LIST:-0}" == "1" ]]; then
    echo "[skip] gh secret list check skipped via STOPIT_SKIP_GH_SECRET_LIST=1"
    return 0
  fi

  if ! command -v gh >/dev/null 2>&1; then
    echo "[skip] gh not installed; skipping GitHub secret name audit"
    return 0
  fi

  if ! gh auth status >/dev/null 2>&1; then
    echo "[skip] gh auth not available; skipping GitHub secret name audit"
    return 0
  fi

  local names_output
  names_output=$(gh secret list --json name --jq '.[].name')

  local missing=()
  local required
  for required in "${REQUIRED_GH_SECRETS[@]}"; do
    if ! grep -Fxq "$required" <<<"$names_output"; then
      missing+=("$required")
    fi
  done

  if (( ${#missing[@]} > 0 )); then
    printf '[fail] missing GitHub repo secrets: %s\n' "${missing[*]}" >&2
    return 1
  fi

  echo "[ok] GitHub secret names present: ${REQUIRED_GH_SECRETS[*]}"
}

run_contract_searches() {
  echo "[run] workflow / notifier / functions secret consumer audit"
  rg -n 'GOOGLE_SERVICES_JSON|DISCORD_BOT_TOKEN|DISCORD_DEPLOY_CHANNEL_ID|GOOGLE_PLAY_SERVICE_ACCOUNT_JSON' .github/workflows
  rg -n 'DISCORD_BOT_TOKEN|DISCORD_DEPLOY_CHANNEL_ID' scripts/notify-discord-deploy.py
  rg -n 'DISCORD_PUBLIC_KEY|DISCORD_DEPLOY_CHANNEL_ID|DISCORD_DEPLOY_ALLOWED_ROLE_IDS|DISCORD_DEPLOY_ALLOWED_USER_IDS|GITHUB_ACTIONS_DISPATCH_TOKEN' functions/src/index.ts
}

run_helper_and_contract_tests() {
  echo "[run] helper syntax + contract regression tests"
  bash -n scripts/setup-play-deploy-secrets.sh scripts/setup-discord-deploy-secrets.sh
  env -u STOPIT_SKIP_GH_SECRET_LIST python3 -m unittest \
    scripts.tests.test_setup_deploy_secret_helpers \
    scripts.tests.test_play_deploy_secret_contract_runbook \
    scripts.tests.test_check_play_deploy_secret_contract -v
}

main() {
  check_github_secret_names
  run_contract_searches
  run_helper_and_contract_tests
  echo "[ok] play deploy secret contract audit finished"
}

main "$@"

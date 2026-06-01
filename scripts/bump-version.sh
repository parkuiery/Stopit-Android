#!/usr/bin/env bash
set -euo pipefail

fetch_origin_main_or_die() {
  if ! git fetch origin main >/dev/null; then
    echo "Failed to fetch origin/main before Android version bump." >&2
    echo "Check network/remote access and retry." >&2
    exit 1
  fi
}

usage() {
  cat <<'USAGE'
Usage: scripts/bump-version.sh <versionName> [--code <versionCode>] [--no-dry-run] [--service-account-json <path>] [--fallback-play-max-version-code <n>]

Examples:
  scripts/bump-version.sh 1.7.2 --service-account-json /path/to/play-service-account.json
  scripts/bump-version.sh 1.7.2 --code 24 --fallback-play-max-version-code 23
USAGE
}

if [[ $# -lt 1 ]]; then
  usage
  exit 2
fi

version_name="$1"
shift
version_code=""
run_dry_run=1
play_service_account_json="${GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH:-}"
fallback_play_max_version_code="${STOPIT_PLAY_MAX_VERSION_CODE:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --code)
      [[ $# -ge 2 ]] || { echo "--code requires a value" >&2; exit 2; }
      version_code="$2"
      shift 2
      ;;
    --no-dry-run)
      run_dry_run=0
      shift
      ;;
    --service-account-json)
      [[ $# -ge 2 ]] || { echo "--service-account-json requires a value" >&2; exit 2; }
      play_service_account_json="$2"
      shift 2
      ;;
    --fallback-play-max-version-code)
      [[ $# -ge 2 ]] || { echo "--fallback-play-max-version-code requires a value" >&2; exit 2; }
      fallback_play_max_version_code="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ ! "$version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "versionName must be SemVer without a leading v, e.g. 1.7.2" >&2
  exit 2
fi

guard_args=()
if [[ -n "$play_service_account_json" ]]; then
  guard_args+=(--service-account-json "$play_service_account_json")
elif [[ -n "$fallback_play_max_version_code" ]]; then
  guard_args+=(--fallback-play-max-version-code "$fallback_play_max_version_code")
else
  echo "Google Play versionCode guard requires --service-account-json, GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH, --fallback-play-max-version-code, or STOPIT_PLAY_MAX_VERSION_CODE." >&2
  exit 2
fi

fetch_origin_main_or_die

version_state="$(python3 - "$version_code" <<'PY'
from pathlib import Path
import json
import re
import subprocess
import sys

requested_code = sys.argv[1]
text = Path('app/build.gradle.kts').read_text()
code_match = re.search(r'versionCode\s*=\s*(\d+)', text)
name_match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
if not code_match or not name_match:
    raise SystemExit('Could not find versionCode/versionName in app/build.gradle.kts')
main_text = subprocess.check_output(['git', 'show', 'origin/main:app/build.gradle.kts'], text=True)
main_code_match = re.search(r'versionCode\s*=\s*(\d+)', main_text)
if not main_code_match:
    raise SystemExit('Could not find origin/main versionCode in app/build.gradle.kts')
old_code = int(code_match.group(1))
main_code = int(main_code_match.group(1))
new_code = int(requested_code) if requested_code else old_code + 1
if new_code <= old_code:
    raise SystemExit(f'versionCode must increase: current={old_code}, requested={new_code}')
print(json.dumps({'old_code': old_code, 'old_name': name_match.group(1), 'new_code': new_code, 'main_code': main_code}))
PY
)"

old_code="$(python3 - <<'PY' "$version_state"
import json
import sys
print(json.loads(sys.argv[1])['old_code'])
PY
)"
new_code="$(python3 - <<'PY' "$version_state"
import json
import sys
print(json.loads(sys.argv[1])['new_code'])
PY
)"
old_name="$(python3 - <<'PY' "$version_state"
import json
import sys
print(json.loads(sys.argv[1])['old_name'])
PY
)"
main_code="$(python3 - <<'PY' "$version_state"
import json
import sys
print(json.loads(sys.argv[1])['main_code'])
PY
)"

python3 scripts/play_version_code_guard.py validate-values \
  --version-code "$new_code" \
  --version-name "$version_name" \
  --minimum-main-version-code "$main_code" \
  "${guard_args[@]}"

python3 - "$version_name" "$new_code" <<'PY'
from pathlib import Path
import re
import sys

version_name = sys.argv[1]
new_code = int(sys.argv[2])
path = Path('app/build.gradle.kts')
text = path.read_text()
text = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {new_code}', text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{version_name}"', text, count=1)
path.write_text(text)
PY

echo "Bumped Android version: $old_name ($old_code) -> $version_name ($new_code)"

if [[ "$run_dry_run" -eq 1 ]]; then
  ./gradlew :app:testProdReleaseUnitTest :app:bundleProdRelease --dry-run
fi

#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/bump-version.sh <versionName> [--code <versionCode>] [--no-dry-run]

Examples:
  scripts/bump-version.sh 1.7.2
  scripts/bump-version.sh 1.7.2 --code 24
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

python3 - "$version_name" "$version_code" <<'PY'
from pathlib import Path
import re
import sys

version_name = sys.argv[1]
requested_code = sys.argv[2]
path = Path("app/build.gradle.kts")
text = path.read_text()

code_match = re.search(r"versionCode\s*=\s*(\d+)", text)
name_match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
if not code_match or not name_match:
    raise SystemExit("Could not find versionCode/versionName in app/build.gradle.kts")

old_code = int(code_match.group(1))
new_code = int(requested_code) if requested_code else old_code + 1
if new_code <= old_code:
    raise SystemExit(f"versionCode must increase: current={old_code}, requested={new_code}")

text = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {new_code}", text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{version_name}"', text, count=1)
path.write_text(text)
print(f"Bumped Android version: {name_match.group(1)} ({old_code}) -> {version_name} ({new_code})")
PY

if [[ "$run_dry_run" -eq 1 ]]; then
  ./gradlew testProdReleaseUnitTest bundleProdRelease --dry-run
fi

#!/usr/bin/env bash
set -euo pipefail

current_branch="$(git branch --show-current)"
version_info="$(python3 - <<'PY'
from pathlib import Path
import re
text = Path('app/build.gradle.kts').read_text()
code = re.search(r'versionCode\s*=\s*(\d+)', text)
name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
if not code or not name:
    raise SystemExit('versionCode/versionName not found')
if not re.fullmatch(r'\d+\.\d+\.\d+', name.group(1)):
    raise SystemExit(f'versionName is not SemVer: {name.group(1)}')
print(f"versionName={name.group(1)} versionCode={code.group(1)}")
PY
)"

echo "Branch: $current_branch"
echo "$version_info"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean:" >&2
  git status --short >&2
  exit 1
fi

if command -v actionlint >/dev/null 2>&1; then
  actionlint
else
  echo "actionlint not installed; skipping workflow lint"
fi

./gradlew testProdReleaseUnitTest bundleProdRelease --dry-run

echo "Release readiness dry-run passed."

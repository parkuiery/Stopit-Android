#!/usr/bin/env bash
set -euo pipefail

fetch_origin_main_or_die() {
  if ! git fetch origin main >/dev/null; then
    echo "Failed to fetch origin/main before release readiness validation." >&2
    echo "Check network/remote access and retry." >&2
    exit 1
  fi
}

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

fetch_origin_main_or_die

main_version_code="$(python3 - <<'PY'
import subprocess
from scripts.play_version_code_guard import parse_build_version_info

text = subprocess.check_output(['git', 'show', 'origin/main:app/build.gradle.kts'], text=True)
print(parse_build_version_info(text).version_code)
PY
)"

echo "Branch: $current_branch"
echo "$version_info"
echo "origin/main versionCode=$main_version_code"

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

python3 scripts/play_version_code_guard.py validate-build \
  --build-file app/build.gradle.kts \
  --minimum-main-version-code "$main_version_code"

./gradlew :app:testProdReleaseUnitTest
./gradlew :app:lintProdRelease
python3 scripts/verify_lint_registry.py \
  --report app/build/reports/lint-results-prodRelease.html \
  --require-section "Included Additional Checks" \
  --require-identifier androidx.navigation.common \
  --require-identifier androidx.navigation.compose \
  --require-identifier androidx.navigation.runtime \
  --require-issue-id MissingSerializableAnnotation \
  --require-issue-id MissingKeepAnnotation \
  --require-issue-id WrongNavigateRouteType \
  --forbid-text "Requires newer lint; these checks will be skipped!" \
  --forbid-text ObsoleteLintCustomCheck
./gradlew :app:bundleProdRelease --dry-run

echo "Release readiness quick preflight passed."
echo "Signed AAB provenance is still verified by the Android Release Build workflow."

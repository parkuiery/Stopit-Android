#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/release-tag.sh <versionName>

Creates and pushes v<versionName> from main. The tag triggers Google Play internal upload.
Example: scripts/release-tag.sh 1.7.2
USAGE
}

if [[ $# -ne 1 ]]; then
  usage
  exit 2
fi

version_name="$1"
tag="v$version_name"

if [[ ! "$version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "versionName must be SemVer without a leading v, e.g. 1.7.2" >&2
  exit 2
fi

branch="$(git branch --show-current)"
if [[ "$branch" != "main" ]]; then
  echo "Release tags must be created from main. Current branch: $branch" >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean. Commit or stash changes first." >&2
  exit 1
fi

scripts/check-latest-production-deployed.sh

git fetch origin main --tags
git pull --ff-only origin main

actual_version="$(python3 - <<'PY'
from pathlib import Path
import re
text = Path('app/build.gradle.kts').read_text()
match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
if not match:
    raise SystemExit('versionName not found')
print(match.group(1))
PY
)"

if [[ "$actual_version" != "$version_name" ]]; then
  echo "versionName mismatch: app has $actual_version, requested $version_name" >&2
  exit 1
fi

if git rev-parse "$tag" >/dev/null 2>&1; then
  echo "Tag already exists locally: $tag" >&2
  exit 1
fi
if git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then
  echo "Tag already exists on origin: $tag" >&2
  exit 1
fi

git tag "$tag"
git push origin "$tag"

echo "Pushed $tag. Watch deployment with:"
echo "  gh run list --workflow play-deploy.yml --limit 1"

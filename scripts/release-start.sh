#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/release-start.sh <versionName> [--code <versionCode>] [--service-account-json <path>] [--fallback-play-max-version-code <n>]

Creates release/<versionName> from develop, bumps Android version, verifies release tasks,
and commits the version bump.

Example:
  scripts/release-start.sh 1.7.2
  scripts/release-start.sh 1.7.2 --code 24
  scripts/release-start.sh 1.7.2 --service-account-json /path/to/play-service-account.json
USAGE
}

if [[ $# -lt 1 ]]; then
  usage
  exit 2
fi

version_name="$1"
shift
extra_args=("$@")

if [[ ! "$version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "versionName must be SemVer without a leading v, e.g. 1.7.2" >&2
  exit 2
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean. Commit or stash changes first." >&2
  exit 1
fi

scripts/check-latest-production-deployed.sh

git fetch origin main develop
git checkout develop
git pull --ff-only origin develop

branch="release/$version_name"
git checkout -b "$branch"

scripts/bump-version.sh "$version_name" "${extra_args[@]}"

git add app/build.gradle.kts
git commit -m "chore: bump version to $version_name"

echo "Prepared $branch. Next:"
echo "  git push -u origin HEAD"
echo "  gh pr create --base main --title 'release: $version_name' --body-file docs/RELEASE_CHECKLIST.md"

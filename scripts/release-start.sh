#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/release-start.sh <versionName> [--code <versionCode>] [--service-account-json <path>] [--fallback-play-max-version-code <n>]

Creates release/<versionName> from develop, bumps Android version, verifies release tasks,
and commits the version bump. Unlike low-level bump-version.sh, release-start never
accepts --no-dry-run because the release branch preparation contract includes the
release task dry-run gate.

Important: release-start delegates to scripts/bump-version.sh, so you must provide
either --service-account-json <path> for live Google Play max versionCode lookup or
--fallback-play-max-version-code <n> as an explicit operator override.

Example:
  scripts/release-start.sh 1.7.2 --service-account-json /path/to/play-service-account.json
  scripts/release-start.sh 1.7.2 --fallback-play-max-version-code 23
  scripts/release-start.sh 1.7.2 --code 24 --service-account-json /path/to/play-service-account.json
USAGE
}

if [[ $# -lt 1 ]]; then
  usage
  exit 2
fi

version_name="$1"
shift
extra_args=("$@")

for arg in "${extra_args[@]}"; do
  if [[ "$arg" == "--no-dry-run" ]]; then
    echo "release-start always runs release task dry-run verification; use scripts/bump-version.sh directly only for low-level maintenance." >&2
    exit 2
  fi
done

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

git add app/build.gradle.kts README.md
git commit -m "chore: bump version to $version_name"

echo "Prepared $branch. Next:"
echo "  git push -u origin HEAD"
echo "  gh pr create --base main --title 'release: $version_name' --body-file docs/RELEASE_CHECKLIST.md"

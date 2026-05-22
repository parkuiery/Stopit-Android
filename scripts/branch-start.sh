#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/branch-start.sh <type> <name>

Types: feature, fix, refactor, docs, test, ci, chore
Example: scripts/branch-start.sh feature emergency-unlock-settings
USAGE
}

if [[ $# -ne 2 ]]; then
  usage
  exit 2
fi

type="$1"
name="$2"

case "$type" in
  feature|fix|refactor|docs|test|ci|chore) ;;
  *) echo "Unsupported branch type: $type" >&2; usage; exit 2 ;;
esac

if [[ ! "$name" =~ ^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$ ]]; then
  echo "Branch name must be kebab-case: $name" >&2
  exit 2
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean. Commit or stash changes first." >&2
  exit 1
fi

git fetch origin develop
git checkout develop
git pull --ff-only origin develop

git checkout -b "$type/$name"

echo "Created branch: $type/$name"

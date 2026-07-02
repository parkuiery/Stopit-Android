#!/usr/bin/env bash
set -euo pipefail

# GitHub Actions ref guard for Android Play Deploy.
# Both direct SemVer tag pushes and manual workflow_dispatch runs must use a
# release tag that is reachable from origin/main and respects the previous
# production-completion marker gate.

event_name="${GITHUB_EVENT_NAME:-}"
ref="${GITHUB_REF:-}"
tag="${GITHUB_REF_NAME:-}"

if [[ "$event_name" != "push" && "$event_name" != "workflow_dispatch" ]]; then
  echo "Skipping Play deploy release guard for event ${event_name:-<unset>}."
  exit 0
fi

if [[ "$ref" != refs/tags/* ]]; then
  echo "Play deploy release guard expected refs/tags/* on ${event_name:-<unset>}; got ${ref:-<unset>}" >&2
  exit 1
fi

if [[ ! "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Play deploy release guard only accepts SemVer tags like v1.7.2; got ${tag:-<unset>}" >&2
  exit 1
fi

git fetch origin main --tags

tag_commit="$(git rev-parse "$tag^{commit}")"
if ! git merge-base --is-ancestor "$tag_commit" origin/main; then
  echo "Play deploy tag $tag must point to a commit reachable from origin/main." >&2
  echo "Create release tags from main via scripts/release-tag.sh after the release PR is merged." >&2
  exit 1
fi

STOPIT_RELEASE_GATE_EXCLUDE_TAG="$tag" scripts/check-latest-production-deployed.sh

echo "Validated Play deploy tag $tag on origin/main with previous production marker gate."

#!/usr/bin/env bash
set -euo pipefail

# GitHub Actions tag-push guard for Android Play Deploy.
# Manual workflow_dispatch ref governance is handled by the dispatch-specific guard;
# this script closes the direct SemVer tag-push path so it cannot bypass the same
# release-tag safety contract used by scripts/release-tag.sh.

event_name="${GITHUB_EVENT_NAME:-}"
ref="${GITHUB_REF:-}"
tag="${GITHUB_REF_NAME:-}"

if [[ "$event_name" != "push" ]]; then
  echo "Skipping tag-push Play deploy guard for event ${event_name:-<unset>}."
  exit 0
fi

if [[ "$ref" != refs/tags/* ]]; then
  echo "Play deploy tag-push guard expected refs/tags/* on push; got ${ref:-<unset>}" >&2
  exit 1
fi

if [[ ! "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Play deploy tag-push guard only accepts SemVer tags like v1.7.2; got ${tag:-<unset>}" >&2
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

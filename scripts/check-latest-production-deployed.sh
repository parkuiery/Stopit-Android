#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/check-latest-production-deployed.sh

Blocks new Stopit release creation unless the latest existing SemVer tag has a
recorded Google Play production deployment marker.

The marker is written by .github/workflows/play-deploy.yml after a successful
production upload, using both GitHub Deployments and a GitHub Release note marker.

Set STOPIT_RELEASE_GATE_BYPASS=1 only for an explicitly approved emergency override.
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "${STOPIT_RELEASE_GATE_BYPASS:-}" == "1" ]]; then
  echo "STOPIT_RELEASE_GATE_BYPASS=1: skipping latest production deployment gate." >&2
  exit 0
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI is required to verify the latest production deployment marker." >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub CLI is not authenticated; run 'gh auth login' before starting a release." >&2
  exit 1
fi

git fetch --tags origin >/dev/null 2>&1 || git fetch --tags >/dev/null

latest_tag="$(git tag -l 'v[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname \
  | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' \
  | { if [[ -n "${STOPIT_RELEASE_GATE_EXCLUDE_TAG:-}" ]]; then grep -Fxv "$STOPIT_RELEASE_GATE_EXCLUDE_TAG"; else cat; fi; } \
  | head -n 1 || true)"

if [[ -z "$latest_tag" ]]; then
  echo "No existing SemVer tag found; latest production deployment gate is not applicable yet."
  exit 0
fi

repo="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"

python3 - "$repo" "$latest_tag" <<'PY'
from __future__ import annotations

import json
import subprocess
import sys
from typing import Any

repo, tag = sys.argv[1], sys.argv[2]
marker = f"<!-- stopit-production-deployed: {tag} -->"


def run(command: list[str], *, allow_fail: bool = False) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode != 0 and not allow_fail:
        raise SystemExit(result.stderr.strip() or f"command failed: {' '.join(command)}")
    return result


def gh_json(command: list[str], *, allow_fail: bool = False) -> Any | None:
    result = run(command, allow_fail=allow_fail)
    if result.returncode != 0 or not result.stdout.strip():
        return None
    return json.loads(result.stdout)


def release_has_marker() -> bool:
    data = gh_json(
        ["gh", "release", "view", tag, "--json", "body,isDraft", "--jq", "."],
        allow_fail=True,
    )
    if not isinstance(data, dict):
        return False
    return not data.get("isDraft") and marker in (data.get("body") or "")


def deployment_has_success() -> bool:
    deployments = gh_json(
        [
            "gh",
            "api",
            f"repos/{repo}/deployments",
            "-f",
            f"ref={tag}",
            "-f",
            "environment=production",
        ],
        allow_fail=True,
    )
    if not isinstance(deployments, list):
        return False

    for deployment in deployments:
        deployment_id = deployment.get("id")
        if not deployment_id:
            continue
        statuses = gh_json(
            ["gh", "api", f"repos/{repo}/deployments/{deployment_id}/statuses"],
            allow_fail=True,
        )
        if not isinstance(statuses, list):
            continue
        if any(status.get("state") == "success" for status in statuses):
            return True
    return False


if deployment_has_success():
    print(f"Latest SemVer tag {tag} has a successful GitHub production deployment marker.")
    raise SystemExit(0)

if release_has_marker():
    print(f"Latest SemVer tag {tag} has a GitHub Release production marker.")
    raise SystemExit(0)

print(
    f"Latest SemVer tag {tag} has not been marked as deployed to production.\n"
    "Stopit release creation is blocked until the previous version reaches Google Play production.\n\n"
    "Next steps:\n"
    f"1. Open the Android Play Deploy run for {tag}.\n"
    "2. Use the Discord production approval button or manually dispatch play-deploy.yml with track=production on the same tag.\n"
    "3. Wait for the production workflow to succeed so it writes the deployment/release marker.\n\n"
    "Emergency-only override: STOPIT_RELEASE_GATE_BYPASS=1 scripts/release-start.sh <version>",
    file=sys.stderr,
)
raise SystemExit(1)
PY

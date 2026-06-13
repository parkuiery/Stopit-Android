#!/usr/bin/env bash
set -euo pipefail

repo="${GITHUB_REPOSITORY:-parkuiery/Stopit-Android}"
environment_name="${STOPIT_PRODUCTION_ENVIRONMENT_NAME:-production}"

echo "Checking GitHub Environment approval protection for ${repo}/${environment_name}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required to verify production Environment approval protection." >&2
  exit 1
fi

if [[ -z "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]]; then
  echo "GH_TOKEN or GITHUB_TOKEN is required to read GitHub Environment protection rules." >&2
  exit 1
fi

required_reviewer_count="$(gh api "repos/${repo}/environments/${environment_name}" \
  --jq '[.protection_rules[]? | select(.type == "required_reviewers")] | length')"

if [[ ! "$required_reviewer_count" =~ ^[0-9]+$ ]]; then
  echo "Unable to parse required reviewer protection rule count for Environment '${environment_name}': ${required_reviewer_count}" >&2
  exit 1
fi

if (( required_reviewer_count < 1 )); then
  {
    echo "## Production Environment approval protection is missing"
    echo ""
    echo "GitHub Environment '${environment_name}' exists, but no required reviewer protection rule was found."
    echo "Production promotion must not continue until repository settings configure Environment '${environment_name}' with required reviewer approval."
    echo ""
    echo "Recovery: GitHub repository Settings -> Environments -> ${environment_name} -> Deployment protection rules -> Required reviewers."
  } >> "${GITHUB_STEP_SUMMARY:-/dev/stderr}"
  echo "Production Environment '${environment_name}' has no required reviewer protection rule; refusing production promotion before Play secrets are decoded." >&2
  exit 1
fi

echo "Production Environment '${environment_name}' has required reviewer protection (${required_reviewer_count} rule(s))."

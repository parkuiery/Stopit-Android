#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/configure-github-repo.sh [--apply]

Configures low-risk repository settings for the Stopit branch strategy.
Without --apply, prints the planned settings.

Settings:
- default branch remains develop
- delete head branches after PR merge
- allow squash merge and rebase merge

Branch protection is intentionally not applied by this script because it can block
emergency fixes. Use GitHub Settings if stricter enforcement is needed.
USAGE
}

apply=0
if [[ $# -gt 1 ]]; then
  usage
  exit 2
fi
if [[ $# -eq 1 ]]; then
  case "$1" in
    --apply) apply=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage; exit 2 ;;
  esac
fi

repo="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
cat <<PLAN
Repository: $repo
Planned settings:
- default_branch=develop
- delete_branch_on_merge=true
- allow_squash_merge=true
- allow_rebase_merge=true
- allow_merge_commit=true
PLAN

if [[ "$apply" -ne 1 ]]; then
  echo "Dry run only. Re-run with --apply to update repository settings."
  exit 0
fi

gh api --method PATCH "repos/$repo" \
  -f default_branch=develop \
  -F delete_branch_on_merge=true \
  -F allow_squash_merge=true \
  -F allow_rebase_merge=true \
  -F allow_merge_commit=true >/dev/null

echo "Repository settings updated."

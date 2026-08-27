#!/usr/bin/env bash
set -euo pipefail

# Local Android runtime gate.
#
# The emulator suites that used to run on every PR now run here. CI no longer
# boots an emulator for pull requests; it verifies that the evidence this script
# writes was produced from the current runtime sources. Because the evidence is
# keyed by a digest of those sources, changing app/runtime code invalidates it
# and forces a rerun -- there is no way to pass CI with stale evidence.
#
# Usage:
#   scripts/runtime-gate.sh              # run the suites, record evidence
#   scripts/runtime-gate.sh --no-batch   # one Gradle invocation per selector
#   scripts/runtime-gate.sh --check      # verify evidence without running
#
# Escalation: when a batched suite fails but no single selector reproduces it,
# the run reports suspected cross-test interference; rerun with --no-batch.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ "${1:-}" == "--check" ]]; then
  exec python3 scripts/android_runtime_suites.py check-evidence
fi

for flavor in dev prod; do
  if [[ ! -f "app/src/${flavor}/google-services.json" ]]; then
    echo "Missing app/src/${flavor}/google-services.json; restore it before running the runtime gate." >&2
    exit 2
  fi
done

python3 scripts/android_runtime_suites.py run-local-gate "$@"

cat <<'NEXT'

Next: commit .runtime-evidence.json alongside your change.
CI's `Runtime evidence` job recomputes the digest and rejects stale evidence.
NEXT

#!/usr/bin/env bash
# A Type=oneshot unit has no start timeout by default (TimeoutStartUSec=infinity): one hung curl, gh,
# docker or psql call keeps the service "activating" forever, and its timer never fires again.
set -euo pipefail
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
missing=0
while IFS= read -r unit; do
  grep -q '^Type=oneshot' "$unit" || continue
  grep -q '^TimeoutStartSec=' "$unit" && continue
  echo "FAIL: $unit is Type=oneshot with no TimeoutStartSec="
  missing=1
done < <(git ls-files '*.service')
[[ $missing == 0 ]] && echo "ok: every oneshot unit has TimeoutStartSec"
exit "$missing"

#!/usr/bin/env bash
# A push that changes what people see carries a before and an after screenshot (owed #22, user
# 12999: "take a screenshot before any visible change"). Visible means a .svelte or .css file
# under web/src, or web/index.html, changed between the merge-base with origin/master and HEAD.
# Proof is the stamp `tools/verify/verify.sh before-after <route>...` writes for HEAD; it shot
# the routes on the base commit and on HEAD. SHOT_SKIP="why nothing visible changed" lets a push
# through and prints the reason.
#
#   tools/quality/shot-gate.sh      # exit 0 = no visible change, a stamp for HEAD, or SHOT_SKIP
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
OUT=${VERIFY_OUT:-/tmp/verify-evidence}

base=$(git merge-base HEAD origin/master 2>/dev/null) || { echo "shot-gate: no origin/master, nothing to compare" >&2; exit 0; }
head=$(git rev-parse HEAD)
visible=$(git diff --name-only "$base" "$head" -- 'web/src/*.svelte' 'web/src/*.css' 'web/index.html')
[ -z "$visible" ] && exit 0

stamp="$OUT/before-after/${head:0:8}/stamp"
if [ -f "$stamp" ] && grep -qx "base=$base" "$stamp" && grep -qx "head=$head" "$stamp"; then
  echo "shot-gate: before/after shots for ${head:0:8}:"
  grep '^png=' "$stamp" | sed 's/^png=/  /'
  exit 0
fi
if [ -n "${SHOT_SKIP:-}" ]; then
  echo "shot-gate: skipped, $SHOT_SKIP"
  exit 0
fi
{
  echo "shot-gate: these files change what people see, and HEAD ${head:0:8} has no before/after shots:"
  printf '  %s\n' $visible
  echo "  Commit, then: tools/verify/verify.sh up && tools/verify/verify.sh before-after '#/today' (each route the change shows)"
  echo "  Nothing visible changed? SHOT_SKIP='why' tools/push.sh ..."
} >&2
exit 1

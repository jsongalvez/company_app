#!/usr/bin/env bash
# wayfinder-park.sh — park uncommitted work so the wayfinder chain can continue.
#
# The chain's spawn gate requires a clean worktree (map #329 ticket #336): the
# daemon never stages or commits, so a session that must stop mid-ticket parks
# its in-flight work here — one command instead of a silent forever-pause.
#
# Usage: scripts/wayfinder-park.sh <context-note>
#   stashes tracked + untracked changes with a canonical message and prints the
#   stash reference to record in the handoff packet before exiting.
set -euo pipefail

[ $# -ge 1 ] || { echo "usage: wayfinder-park.sh <context-note>" >&2; exit 2; }

if [ -z "$(git status --porcelain)" ]; then
  echo "worktree already clean — nothing to park"
  exit 0
fi

msg="wip #park $(date '+%F %T') — $*"
git stash push -u -m "$msg"
echo ""
echo "Parked as:"
git stash list | head -1
echo ""
echo "Now: record the stash line above in your handoff packet, commit nothing else, exit."

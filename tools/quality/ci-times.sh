#!/usr/bin/env bash
# Seconds per job and per slow step for recent runs of one workflow, read from the GitHub API.
# Jobs run in parallel: only the job named on "wall set by" makes CI slower, so speed that one first.
#   tools/quality/ci-times.sh            # last 10 quality runs
#   tools/quality/ci-times.sh web 5
set -euo pipefail
wf=${1:-quality}
n=${2:-10}
repo=$(gh repo view --json nameWithOwner -q .nameWithOwner)
dur='((.completed_at|fromdate)-(.started_at|fromdate))'
gh run list -w "$wf" -L "$n" --json databaseId,headSha,conclusion -q '.[]|"\(.databaseId) \(.headSha[0:8]) \(.conclusion)"' |
while read -r id sha conclusion; do
  wall=$(gh api "repos/$repo/actions/runs/$id" -q '((.updated_at|fromdate)-(.run_started_at|fromdate))')
  echo "$sha $conclusion wall=${wall}s"
  gh api "repos/$repo/actions/runs/$id/jobs" -q "[.jobs[]|select(.completed_at)] as \$j|(\$j[]|\"  \(.name)=\($dur)s: \([.steps[]|select(.completed_at)|select($dur>5)|\"\(.name[0:30])=\($dur)\"]|join(\", \"))\"),(\$j|max_by($dur)|\"  wall set by: \(.name)\")"
done

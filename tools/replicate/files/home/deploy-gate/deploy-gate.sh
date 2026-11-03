#!/usr/bin/env bash
# Deploys company_app with Coolify only when master's newest commit passed
# GitHub: the browser run green, and every other run on it (quality when it
# ran; jmh ignored) finished green. Run by company-app-deploy-gate.timer.
set -euo pipefail
. "$HOME/.config/company-app-deploy.env"   # COOLIFY_TOKEN, COOLIFY_URL, COOLIFY_APP_UUID
REPO=jsongalvez/company_app
STATE="$HOME/.local/state/deploy-gate"; mkdir -p "$STATE"
sha=$(gh api "repos/$REPO/commits/master" --jq .sha)
[[ "$(cat "$STATE/last" 2>/dev/null)" == "$sha" ]] && exit 0
runs=$(gh run list -R "$REPO" --commit "$sha" --json workflowName,status,conclusion \
  --jq '.[] | select(.workflowName != "jmh") | "\(.workflowName) \(.status) \(.conclusion)"')
grep -q '^browser ' <<<"$runs" || exit 0                       # not started yet
grep -qv ' completed ' <<<"$runs" && exit 0                    # still running
if grep -qv ' completed success$' <<<"$runs"; then
  [[ "$(cat "$STATE/red" 2>/dev/null)" == "$sha" ]] || { echo "not deploying ${sha:0:8}: $runs" | tr '\n' ';'; echo; echo "$sha" > "$STATE/red"; }
  exit 0
fi
# Master moved while we looked: the next tick checks the new commit.
[[ "$(gh api "repos/$REPO/commits/master" --jq .sha)" == "$sha" ]] || exit 0
out=$(curl -s -m30 -X POST -w '\n%{http_code}' -H "Authorization: Bearer $COOLIFY_TOKEN" "$COOLIFY_URL/api/v1/deploy?uuid=$COOLIFY_APP_UUID&force=false")
code=${out##*$'\n'}
if [[ "$code" != 2* ]]; then echo "deploy ${sha:0:8} refused [$code]: ${out%$'\n'*}" | cut -c1-400; exit 22; fi
echo "deploying ${sha:0:8}: ${out%$'\n'*}" | cut -c1-300
echo "$sha" > "$STATE/last"

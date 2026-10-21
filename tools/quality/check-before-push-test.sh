#!/usr/bin/env bash
# DB-free self-test: tools/check-before-push.sh must cover every Gradle task and every
# shell self-test that CI's quality workflow runs, except the documented DB-needing ones.
# Fails when someone adds a CI check and the local mirror silently misses it.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

plan=$(tools/check-before-push.sh --all --plan)
status=0

# Loose, parser-independent scan: every :module:task word and every `./gradlew <task>`
# anywhere in the non-comment lines of the workflow. check-before-push.sh parses one run
# block; this catches a new step written in a shape that parser does not read.
ci=$(grep -vE '^$' <<< "$(
  { grep -vE '^[[:space:]]*#' .github/workflows/quality.yml | grep -oE '[[:space:]]:[a-zA-Z-]+:[a-zA-Z]+' ;
    grep -vE '^[[:space:]]*#' .github/workflows/quality.yml | grep -oE '\./gradlew +[a-zA-Z]+' | awk '{print $2}'; } | tr -d ' ' | sort -u)")
[ -n "$ci" ] || { echo "check-before-push-test: parsed no CI tasks" >&2; exit 1; }

for t in $ci; do
  case "$t" in
    :backend:test|:backend:verifyOpenApiContract) continue ;; # need CI Postgres
  esac
  if ! grep -q -- "$t" <<< "$plan"; then
    echo "check-before-push-test: CI runs $t but tools/check-before-push.sh --all does not" >&2
    status=1
  fi
done

for s in $(grep -E '^[[:space:]]+bash tools/.*\.sh$' .github/workflows/quality.yml | awk '{print $2}'); do
  grep -q -- "$s" <<< "$plan" || { echo "check-before-push-test: CI shell test $s missing locally" >&2; status=1; }
done

# -Werror must be on, or the local run proves nothing about CI.
grep -q -- '-PwarningsAsErrors=true' <<< "$plan" || { echo "check-before-push-test: -PwarningsAsErrors=true missing" >&2; status=1; }

[ $status -eq 0 ] && echo "check-before-push-test: OK"
exit $status

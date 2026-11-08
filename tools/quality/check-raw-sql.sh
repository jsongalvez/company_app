#!/usr/bin/env bash
# Fails when server code runs SQL text instead of Exposed's typed tables: `exec(`, `execInBatch(`,
# `prepareStatement(` or `createStatement(` in backend/src/main. SQL in a string skips the column
# types, so a renamed column or a spliced value only shows up at run time.
#
# The few statements Exposed has no form for are listed in tools/quality/raw-sql-allow.txt as
# "<path> <count> <reason>". A listed file may not gain a call; a stale line fails too.
#
# Usage:
#   tools/quality/check-raw-sql.sh                         # checks this repo
#   tools/quality/check-raw-sql.sh --root DIR --allow FILE # used by the self-test
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ALLOW=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) ROOT="$2"; shift ;;
    --allow) ALLOW="$2"; shift ;;
    -h|--help) sed -n 2,11p "$0"; exit 0 ;;
    *) echo "check-raw-sql: unknown argument $1" >&2; exit 2 ;;
  esac
  shift
done
[ -n "$ALLOW" ] || ALLOW="$ROOT/tools/quality/raw-sql-allow.txt"
[ -f "$ALLOW" ] || ALLOW=/dev/null

cd "$ROOT"
pattern='\b(exec|execInBatch|prepareStatement|createStatement)\('
declare -A found=()
while IFS= read -r path; do
  n=$(grep -cE "$pattern" "$path" || true)
  [ "$n" -gt 0 ] && found[$path]=$n
done < <(git ls-files -- 'backend/src/main/*.kt' 'backend/src/main/**/*.kt')

status=0
fail() { echo "check-raw-sql: $1" >&2; status=1; }
declare -A allowed=()
while read -r path n _reason; do
  case "$path" in ''|'#'*) continue ;; esac
  allowed[$path]=$n
  if [ -z "${found[$path]:-}" ]; then
    fail "$path is listed in $ALLOW but runs no SQL text: delete its line"
  elif [ "${found[$path]}" -gt "$n" ]; then
    fail "$path runs SQL text ${found[$path]} times, listed for $n: use Exposed's typed tables"
  elif [ "${found[$path]}" -lt "$n" ]; then
    fail "$path runs SQL text ${found[$path]} times, listed for $n: lower its count"
  fi
done < "$ALLOW"
for path in "${!found[@]}"; do
  [ -n "${allowed[$path]:-}" ] && continue
  grep -nE "$pattern" "$path" | sed "s|^|check-raw-sql: $path:|" >&2
  fail "$path runs SQL text: use Exposed's typed tables (select, insert, update, joins, unionAll)"
done
[ $status -eq 0 ] && echo "check-raw-sql: OK (${#allowed[@]} listed files run statements Exposed has no form for)"
exit $status

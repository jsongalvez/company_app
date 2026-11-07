#!/usr/bin/env bash
# Fails when a tracked source file passes MAX lines (default 1000). Agents copy what exists and
# grow the file they are in; a cap keeps each file small enough to read whole.
#
# Files already over the cap are listed in tools/quality/file-size-allow.txt as "<lines> <path>".
# A listed file may shrink but never grow past its recorded count. When it shrinks, lower the
# count; when it is back under MAX or gone, delete its line (a stale line fails too).
# Split a big file instead of raising its count.
#
# Usage:
#   tools/quality/check-file-size.sh                         # checks this repo
#   tools/quality/check-file-size.sh --fix                   # lowers counts of shrunk files, drops lines of files back under the cap
#   tools/quality/check-file-size.sh --root DIR --allow FILE # used by the self-test
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ALLOW=""
MAX=1000
FIX=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) ROOT="$2"; shift ;;
    --allow) ALLOW="$2"; shift ;;
    --max) MAX="$2"; shift ;;
    --fix) FIX=1 ;;
    -h|--help) sed -n 2,14p "$0"; exit 0 ;;
    *) echo "check-file-size: unknown argument $1" >&2; exit 2 ;;
  esac
  shift
done
[ -n "$ALLOW" ] || ALLOW="$ROOT/tools/quality/file-size-allow.txt"
[ -f "$ALLOW" ] || ALLOW=/dev/null

cd "$ROOT"
# Source code only. Generated files (OpenAPI types) and data files are not code anyone edits.
sources=$(git ls-files -- '*.kt' '*.kts' '*.ts' '*.svelte' '*.js' '*.mjs' '*.cjs' '*.py' '*.sh' '*.sql' \
  | grep -vE '(^|/)(node_modules|build|dist)/' | grep -vxF web/src/api/schema.d.ts || true)

status=0
fixed=0
fail() { echo "check-file-size: $1" >&2; status=1; }

declare -A allowed=()
while read -r n path; do
  case "$n" in ''|'#'*) continue ;; esac
  allowed["$path"]=$n
done < "$ALLOW"

while IFS= read -r f; do
  [ -f "$f" ] || continue
  n=$(wc -l < "$f")
  cap=${allowed["$f"]:-$MAX}
  if [ "$n" -gt "$cap" ]; then
    if [ -n "${allowed[$f]:-}" ]; then
      fail "$f has $n lines, more than its allowed $cap. Split it; do not raise the count."
    else
      fail "$f has $n lines, more than $MAX. Split it into smaller files."
    fi
  elif [ -n "${allowed[$f]:-}" ] && [ "$n" -lt "$cap" ]; then
    if [ "$FIX" = 1 ]; then
      if [ "$n" -le "$MAX" ]; then unset "allowed[$f]"; else allowed["$f"]=$n; fi
      fixed=1
    elif [ "$n" -le "$MAX" ]; then
      fail "$f is down to $n lines: delete its line from $(basename "$ALLOW")."
    else
      fail "$f shrank to $n lines: lower its count in $(basename "$ALLOW") to $n."
    fi
  fi
done <<< "$sources"

for f in "${!allowed[@]}"; do
  grep -qxF -- "$f" <<< "$sources" || fail "$f is listed in $(basename "$ALLOW") but is not a tracked source file: delete its line."
done

if [ "$fixed" = 1 ]; then
  { grep -E '^(#|[[:space:]]*$)' "$ALLOW" || true
    for f in "${!allowed[@]}"; do echo "${allowed[$f]} $f"; done | sort -k2; } > "$ALLOW.new"
  mv "$ALLOW.new" "$ALLOW"
  echo "check-file-size: --fix rewrote $(basename "$ALLOW")"
fi

[ $status -eq 0 ] && echo "check-file-size: OK (max $MAX lines, ${#allowed[@]} older files on the shrink-only list)"
exit $status

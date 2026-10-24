#!/usr/bin/env bash
# DB-free self-test for check-patterns.sh. The past mistake it guards against: rows 5 and 6 of
# docs/patterns.md asked for new levers while master already had every feature they named.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHECK="$ROOT/tools/quality/check-patterns.sh"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
status=0
SHA=$(git -C "$ROOT" rev-parse --short HEAD)

expect() { # name, expected rc, expected text
  local out rc=0
  out=$("$CHECK" --file "$T/p.md" 2>&1) || rc=$?
  if [ "$rc" -ne "$2" ] || ! grep -q -- "$3" <<< "$out"; then
    echo "check-patterns-test: $1: wanted rc=$2 with '$3', got rc=$rc: $out" >&2
    status=1
  fi
}

table() { # rows: "pattern|still-happens cell"
  printf '# Patterns log\n\n## Open\n\n| Pattern | Seen in | Count | Possible lever | Still happens? (checked at SHA) | Approved? |\n| --- | --- | --- | --- | --- | --- |\n' > "$T/p.md"
  local r
  for r in "$@"; do printf '| %s | x | 1 | x | %s | yes |\n' "${r%%|*}" "${r#*|}" >> "$T/p.md"; done
  printf '\n## Done\n\n| Pattern | Lever | Commit |\n| --- | --- | --- |\n| Old | x | PR #1 |\n' >> "$T/p.md"
}

# The real log before the column existed: rows 5 and 6 sat in Open unchecked.
git -C "$ROOT" show 0b43ce6d:docs/patterns.md > "$T/p.md"
expect "log at 0b43ce6d" 1 "no \"Still happens? (checked at SHA)\" column"

table "Layout breaks|yes, inferred: report (checked at $SHA)" "A basic action is missing|" "A screen lacks context|"
expect "rows 5 and 6 blank" 1 "Open row 2 (A basic action is missing"

table "A basic action is missing|no: all exist (checked at $SHA)"
expect "answered no but still Open" 1 "move it out of Open"

table "Layout breaks|yes: report 1 (checked at deadbee)"
expect "unknown commit" 1 "not a commit in this repo"

table "Layout breaks|yes: report 1"
expect "no commit named" 1 "checked at <sha>"

table "Layout breaks|maybe (checked at $SHA)"
expect "not yes or no" 1 "must start with yes or no"

table "Layout breaks|yes, inferred: report 1 (checked at $SHA)"
expect "answered rows" 0 "OK (1 open rows)"

cp "$ROOT/docs/patterns.md" "$T/p.md"
expect "current log" 0 "OK"

[ "$status" -eq 0 ] && echo "check-patterns-test: OK"
exit "$status"

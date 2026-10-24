#!/usr/bin/env bash
# Fails when an Open row in docs/patterns.md does not say whether the mistake still happens on
# a named commit. Agents proposed rules for problems the code had already fixed: rows 5 and 6
# of the first round (missing basic actions, screens lacking context) asked for a checklist
# while every feature they named was already on master (checked at 0b43ce6d).
#
# Each Open row needs a "Still happens? (checked at SHA)" cell that:
#   - starts with "yes" or "no", then the evidence ("yes, inferred: ..." is fine),
#   - names "checked at <sha>", a commit that exists in this repo.
# A row whose answer is "no" may not stay in Open: move it to "## Not happening".
#
# Usage:
#   tools/quality/check-patterns.sh              # checks docs/patterns.md
#   tools/quality/check-patterns.sh --file PATH  # checks another copy (used by the self-test)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FILE="$ROOT/docs/patterns.md"
while [ $# -gt 0 ]; do
  case "$1" in
    --file) FILE="$2"; shift ;;
    -h|--help) sed -n 2,14p "$0"; exit 0 ;;
    *) echo "check-patterns: unknown argument $1" >&2; exit 2 ;;
  esac
  shift
done
[ -f "$FILE" ] || { echo "check-patterns: no file $FILE" >&2; exit 2; }

COLUMN='Still happens? (checked at SHA)'
status=0
fail() { echo "check-patterns: $1" >&2; status=1; }

# Print the Open table as "row<TAB>pattern<TAB>still-happens cell", or "NOCOLUMN".
rows=$(awk -v col="$COLUMN" '
  /^## / { open = ($0 == "## Open"); next }
  !open || !/^\|/ { next }
  {
    n = split($0, c, "|"); for (i = 2; i < n; i++) { gsub(/^ +| +$/, "", c[i]) }
    if (!seen_header) {
      seen_header = 1; idx = 0
      for (i = 2; i < n; i++) if (c[i] == col) idx = i
      if (!idx) { print "NOCOLUMN"; exit }
      next
    }
    if (c[2] ~ /^-+$/) next
    row++; print row "\t" substr(c[2], 1, 60) "\t" c[idx]
  }' "$FILE")

if [ "$rows" = "NOCOLUMN" ]; then
  fail "the Open table in ${FILE#"$ROOT"/} has no \"$COLUMN\" column; add it before \"Approved?\""
  exit 1
fi

while IFS=$'\t' read -r row pattern cell; do
  [ -n "$row" ] || continue
  where="Open row $row ($pattern...)"
  if [ -z "$cell" ]; then
    fail "$where: \"$COLUMN\" is empty. Check master first: answer yes or no with the evidence and \"checked at <sha>\""
    continue
  fi
  if [[ ! "$cell" =~ ^(yes|no)([^a-z]|$) ]]; then
    fail "$where: \"$COLUMN\" must start with yes or no, got: $cell"
    continue
  fi
  if [[ ! "$cell" =~ checked\ at\ ([0-9a-f]{7,40}) ]]; then
    fail "$where: \"$COLUMN\" must name the commit it was checked on, as \"checked at <sha>\""
    continue
  fi
  sha="${BASH_REMATCH[1]}"
  if ! git -C "$ROOT" cat-file -e "$sha^{commit}" 2>/dev/null; then
    fail "$where: checked at $sha, which is not a commit in this repo"
    continue
  fi
  if [[ "$cell" =~ ^no ]]; then
    fail "$where: it no longer happens, so move it out of Open to \"## Not happening\""
  fi
done <<< "$rows"

[ "$status" -eq 0 ] && echo "check-patterns: OK ($(grep -c . <<< "$rows") open rows)"
exit "$status"

#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
    printf 'Usage: %s <map-number> <child-number>\n' "$0" >&2
    exit 2
fi

map_number="$1"
child_number="$2"
repo="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
expected="https://api.github.com/repos/$repo/issues/$map_number"
actual="$(gh api "repos/$repo/issues/$child_number" --jq '.parent_issue_url // ""')"

if [ "$actual" != "$expected" ]; then
    printf 'Native parent mismatch: child #%s has %s; expected %s\n' "$child_number" "$actual" "$expected" >&2
    exit 1
fi

label="$(gh api "repos/$repo/issues/$child_number" --jq '[.labels[].name | select(startswith("wayfinder:"))] | first // ""')"
[ -n "$label" ] || { printf 'Child #%s has no wayfinder label\n' "$child_number" >&2; exit 1; }
printf 'Verified child #%s: parent #%s, label %s\n' "$child_number" "$map_number" "$label"

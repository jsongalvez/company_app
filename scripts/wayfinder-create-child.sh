#!/usr/bin/env bash
set -euo pipefail

usage() {
    printf 'Usage: %s <map-number> <type> <title> <body-file>\n' "$0" >&2
    printf 'Types: research, prototype, grilling, task\n' >&2
    exit 2
}

[ "$#" -eq 4 ] || usage
map_number="$1"
type="$2"
title="$3"
body_file="$4"

case "$type" in
    research|prototype|grilling|task) ;;
    *) usage ;;
esac
[ -f "$body_file" ] || { printf 'Body file not found: %s\n' "$body_file" >&2; exit 1; }

repo="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
map_url="https://github.com/$repo/issues/$map_number"
child_url="$(gh issue create --repo "$repo" --title "$title" --body-file "$body_file" --label "wayfinder:$type")"
child_number="${child_url##*/}"
child_id="$(gh api "repos/$repo/issues/$child_number" --jq .id)"

gh api --method POST "repos/$repo/issues/$map_number/sub_issues" \
    -F "sub_issue_id=$child_id" >/dev/null

parent_url="$(gh api "repos/$repo/issues/$child_number" --jq '.parent_issue_url // ""')"
if [ "$parent_url" != "https://api.github.com/repos/$repo/issues/$map_number" ]; then
    printf 'Child created but native parent verification failed: %s\n' "$child_url" >&2
    exit 1
fi

printf '%s\n' "$child_url"
printf 'Native parent verified: %s\n' "$map_url" >&2

#!/bin/bash
set -euo pipefail

files="$(cat)"
if [ -z "$files" ]; then
    printf 'gate-sensitive\n'
    exit 0
fi

found_file=false
while IFS= read -r file; do
    [ -n "$file" ] || continue
    found_file=true
    if [[ "$file" =~ (^|/)AGENTS\.md$ ||
        "$file" = CONTEXT.md ||
        "$file" =~ ^README[^/]*\.md$ ||
        "$file" = CONTRIBUTING.md ||
        "$file" = CHANGELOG.md ||
        "$file" =~ ^docs/.+\.md$ ||
        "$file" =~ ^\.opencode/.+\.md$ ]]; then
        continue
    else
        printf 'gate-sensitive\n'
        exit 0
    fi
done <<< "$files"

if [ "$found_file" = false ]; then
    printf 'gate-sensitive\n'
    exit 0
fi

printf 'docs-only\n'

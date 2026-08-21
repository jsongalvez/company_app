#!/usr/bin/env bash
set -euo pipefail

find /tmp -maxdepth 1 -type f -name '.[0-9a-f]*.so' -mmin +10 -print0 |
    while IFS= read -r -d '' file; do
        if ! /usr/bin/fuser -s "$file"; then
            /usr/bin/rm -f -- "$file"
        fi
    done

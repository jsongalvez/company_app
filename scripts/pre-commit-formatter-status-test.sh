#!/bin/bash
set -euo pipefail

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

printf '%s\n' '#!/bin/bash' 'exit 7' > "$WORK_DIR/ktlint"
chmod +x "$WORK_DIR/ktlint"

if printf '%s\0' sample.kt | xargs -0 "$WORK_DIR/ktlint" --format 2>&1 | tail -3; then
    printf 'pre-commit formatter failure fixture: FAIL\n' >&2
    exit 1
fi

printf 'pre-commit formatter failure fixture: PASS\n'

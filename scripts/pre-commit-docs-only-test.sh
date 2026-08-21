#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
GIT_CALLS="$WORK_DIR/git-calls"

printf '%s\n' \
    '#!/bin/bash' \
    'if [ "$1" = "diff" ]; then' \
    '    printf '\''git\n'\'' >> "$GIT_CALLS"' \
    '    printf '\''%s\n'\'' docs/agents/example.md' \
    'else' \
    '    printf '\''unexpected git invocation\n'\'' >&2' \
    '    exit 1' \
    'fi' > "$WORK_DIR/git"
chmod +x "$WORK_DIR/git"

output="$(GIT_CALLS="$GIT_CALLS" PATH="$WORK_DIR:$PATH" bash "$ROOT_DIR/.githooks/pre-commit")"
grep -Fq 'Staged change classification: docs-only' <<<"$output"
grep -Fq 'Documentation-only staged change; skipping' <<<"$output"
[ "$(wc -l < "$GIT_CALLS")" -eq 1 ]

printf 'pre-commit docs-only fixture: PASS\n'

#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
GIT_CALLS="$WORK_DIR/git-calls"

# Fixture: one staged docs file. Hook must still run its cheap checks — the
# docs-only fast path (classify-push-files.sh) was deleted with #339; hooks
# are near-zero for every change type (map #329).
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
grep -Fq 'No staged Kotlin files' <<<"$output"
grep -Fq 'Pre-commit checks passed' <<<"$output"
[ "$(wc -l < "$GIT_CALLS")" -eq 2 ]

printf 'pre-commit docs-only fixture: PASS\n'

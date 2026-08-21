#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLASSIFIER="$ROOT_DIR/scripts/classify-push-files.sh"

run_case() {
    local name="$1"
    local expected="$2"
    shift 2
    local actual
    actual="$(printf '%s\n' "$@" | bash "$CLASSIFIER")"
    if [ "$actual" != "$expected" ]; then
        printf 'case %s: expected %s, got %s\n' "$name" "$expected" "$actual" >&2
        exit 1
    fi
}

run_case docs-only docs-only \
    docs/agents/wayfinder-225-handoff.md \
    AGENTS.md \
    backend/AGENTS.md \
    CONTEXT.md \
    .opencode/skills/example/SKILL.md
run_case mixed gate-sensitive docs/README.md backend/src/main/kotlin/App.kt
run_case deleted-source gate-sensitive docs/README.md backend/src/main/kotlin/Removed.kt
run_case renamed-source gate-sensitive docs/README.md backend/src/main/kotlin/Renamed.kt
run_case unusual-path gate-sensitive $'docs/agents/line\nname.md'
run_case build-doc gate-sensitive build.gradle.kts
run_case script-doc gate-sensitive scripts/README.md
run_case workflow-doc gate-sensitive .github/workflows/README.md
run_case empty gate-sensitive

printf 'push classification fixtures: PASS\n'

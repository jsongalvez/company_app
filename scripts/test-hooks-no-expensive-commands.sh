#!/bin/bash
# Guard-rail fixtures for the near-zero-cost git hooks (map #329).
#
# Proves that .githooks/pre-commit and .githooks/pre-push never execute
# Gradle, psql/database queries, container/backend startup, network clients,
# or k6 — by putting failing shims for those tools first on PATH and
# asserting the hooks complete without invoking any of them.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

BANNED_TOOLS=(gradle gradlew psql docker k6 curl wget pg_isready)

die() { printf 'test-hooks-no-expensive-commands: %s\n' "$*" >&2; exit 1; }

# Failing shims: each records its tool name in markers/ before exiting 99.
make_shims() {
    local fake="$1" tool
    mkdir -p "$fake/markers"
    for tool in "${BANNED_TOOLS[@]}"; do
        cat > "$fake/$tool" <<EOF
#!/bin/sh
mkdir -p "$fake/markers"
touch "$fake/markers/$tool"
echo "banned tool invoked: $tool" >&2
exit 99
EOF
        chmod +x "$fake/$tool"
    done
}

markers_empty() {
    [ -z "$(ls "$1/markers" 2>/dev/null)" ]
}

new_sandbox_repo() {
    SANDBOX="$WORK_DIR/sandbox-$1"
    mkdir -p "$SANDBOX" "$WORK_DIR/home-$1"
    git -C "$SANDBOX" init -q
    git -C "$SANDBOX" config user.email test@example.com
    git -C "$SANDBOX" config user.name Test
}

# Runs $2 (a hook script) inside the current SANDBOX with the shim dir for
# $3 first on PATH and an isolated HOME (no ktlint cache).
run_hook() {
    local hook="$1" label="$2" fake="$WORK_DIR/fake-$3"
    (
        cd "$SANDBOX"
        export PATH="$fake:$PATH"
        export HOME="$WORK_DIR/home-$label"
        if [ -n "${STDIN_FEED:-}" ]; then
            printf '%s' "$STDIN_FEED" | bash "$hook"
        else
            bash "$hook"
        fi
    )
}

# --- Fixture 1: pre-commit with staged Kotlin + shell files -----------------
# ktlint is absent (isolated HOME, nothing on PATH), so the hook must warn,
# skip formatting, still syntax-check the shell file, and invoke nothing banned.
make_shims "$WORK_DIR/fake-commit"
new_sandbox_repo commit
printf 'val x = 1\n' > "$SANDBOX/src.kt"
printf '#!/bin/bash\ntrue\n' > "$SANDBOX/tool.sh"
git -C "$SANDBOX" add src.kt tool.sh
out="$(run_hook "$ROOT_DIR/.githooks/pre-commit" commit commit 2>&1)" || die "pre-commit failed unexpectedly: $out"
grep -q 'WARNING: ktlint not found' <<<"$out" || die "expected ktlint-missing warning, got: $out"
grep -q 'Checking staged shell syntax' <<<"$out" || die "expected shell syntax check, got: $out"
markers_empty "$WORK_DIR/fake-commit" || die "pre-commit invoked banned tools: $(ls "$WORK_DIR/fake-commit/markers")"

# --- Fixture 2: pre-push ignores refs and runs no gates ---------------------
make_shims "$WORK_DIR/fake-push"
new_sandbox_repo push
STDIN_FEED='refs/heads/master old refs/heads/master new
' run_hook "$ROOT_DIR/.githooks/pre-push" push push >/dev/null 2>&1 || die "pre-push failed unexpectedly"
markers_empty "$WORK_DIR/fake-push" || die "pre-push invoked banned tools: $(ls "$WORK_DIR/fake-push/markers")"

# --- Fixture 3 (negative control): retired hooks must trip the probe --------
# The fixtures above are only meaningful if the same probe catches the old
# gates-heavy hooks replayed from history.
OLD_COMMIT="$WORK_DIR/old-pre-commit"
OLD_PUSH="$WORK_DIR/old-pre-push"
git -C "$ROOT_DIR" show 'HEAD:.githooks/pre-commit' > "$OLD_COMMIT" 2>/dev/null || true
git -C "$ROOT_DIR" show 'HEAD:.githooks/pre-push' > "$OLD_PUSH" 2>/dev/null || true

if [ -f "$OLD_COMMIT" ]; then
    chmod +x "$OLD_COMMIT"
    # The retired hook shells out to ./gradlew relative to the working tree;
    # plant the shim there so the invocation is captured instead of resolved
    # from the real repo root.
    cp "$WORK_DIR/fake-commit/gradlew" "$SANDBOX/gradlew"
    chmod +x "$SANDBOX/gradlew"
    if run_hook "$OLD_COMMIT" commit commit >/dev/null 2>&1; then
        die "negative control failed: retired pre-commit passed the banned-tool probe"
    fi
else
    die "could not replay retired pre-commit from HEAD for the negative control"
fi

if [ -f "$OLD_PUSH" ]; then
    chmod +x "$OLD_PUSH"
    STDIN_FEED='refs/heads/master old refs/heads/master new
'
    if run_hook "$OLD_PUSH" push push >/dev/null 2>&1; then
        die "negative control failed: retired pre-push passed the banned-tool probe"
    fi
else
    die "could not replay retired pre-push from HEAD for the negative control"
fi

printf 'PASS: hooks never invoke Gradle, psql, containers, network clients, or k6\n'

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

# --- Fixture 3 (negative controls): the probe must catch gate-running hooks --
# 3a: a synthetic hook that invokes banned tools must trip the probe.
# 3b: the actual retired hooks (parent of the #330 rewrite) must trip it too,
#     replayed when that history is available locally.
SYNTHETIC="$WORK_DIR/synthetic-hook"
cat > "$SYNTHETIC" <<'EOF'
#!/bin/bash
./gradlew --version
psql --version
EOF
chmod +x "$SYNTHETIC"
if run_hook "$SYNTHETIC" commit commit >/dev/null 2>&1; then
    die "negative control failed: synthetic gate-running hook passed the probe"
fi
markers_empty "$WORK_DIR/fake-commit" && die "probe caught synthetic hook but recorded no tool markers"

OLD_COMMIT="$WORK_DIR/old-pre-commit"
OLD_PUSH="$WORK_DIR/old-pre-push"
# 52d57bbf is the #330 hooks rewrite; its parent still carries the gates-heavy hooks.
if git -C "$ROOT_DIR" cat-file -e '52d57bbf^:.githooks/pre-commit' 2>/dev/null; then
    git -C "$ROOT_DIR" show '52d57bbf^:.githooks/pre-commit' > "$OLD_COMMIT"
    git -C "$ROOT_DIR" show '52d57bbf^:.githooks/pre-push' > "$OLD_PUSH"
    chmod +x "$OLD_COMMIT" "$OLD_PUSH"
    # The retired pre-commit shells out to ./gradlew relative to the working tree;
    # plant the shim there so the invocation is captured instead of resolved from
    # the real repo root.
    cp "$WORK_DIR/fake-commit/gradlew" "$SANDBOX/gradlew"
    chmod +x "$SANDBOX/gradlew"
    if run_hook "$OLD_COMMIT" commit commit >/dev/null 2>&1; then
        die "negative control failed: retired pre-commit passed the banned-tool probe"
    fi
    STDIN_FEED='refs/heads/master old refs/heads/master new
'
    if run_hook "$OLD_PUSH" push push >/dev/null 2>&1; then
        die "negative control failed: retired pre-push passed the banned-tool probe"
    fi
    printf 'negative controls: retired hooks from 52d57bbf^ tripped the probe\n'
else
    printf 'negative controls: retired-hook history unavailable; synthetic control only\n'
fi

printf 'PASS: hooks never invoke Gradle, psql, containers, network clients, or k6\n'

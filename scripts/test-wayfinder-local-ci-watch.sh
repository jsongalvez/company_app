#!/usr/bin/env bash
# Script-level contract test for the #577 local-CI pin/watch path.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d /tmp/opencode/wayfinder-local-ci-test.XXXXXX)"
OLD_PID=""
trap 'if [ -n "$OLD_PID" ]; then kill "$OLD_PID" 2>/dev/null || true; fi; rm -rf "$WORK"' EXIT

die() {
    printf 'test-wayfinder-local-ci-watch: %s\n' "$*" >&2
    exit 1
}

assert_eq() {
    local expected=$1 actual=$2 message=${3:-values differ}
    [ "$expected" = "$actual" ] || die "$message (expected '$expected', got '$actual')"
}

# local-ci.sh pins the run at entry, even when a gate moves HEAD mid-run.
PIN_REPO="$WORK/pin-repo"
mkdir -p "$PIN_REPO/scripts" "$PIN_REPO/docker" "$WORK/bin"
cp "$ROOT/scripts/local-ci.sh" "$PIN_REPO/scripts/"
printf 'DB_HOST=127.0.0.1\nDB_PORT=5432\n' >"$PIN_REPO/.env"
cat >"$PIN_REPO/docker/docker-compose.yml" <<'EOF'
services: {}
EOF
cat >"$PIN_REPO/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [ ! -f .head-moved ]; then
    : >.head-moved
    git add .head-moved
    git -c user.email=test@example.invalid -c user.name=test commit -q -m move-head
fi
EOF
cat >"$WORK/bin/docker" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat >"$WORK/bin/pg_isready" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$PIN_REPO/gradlew" "$WORK/bin/docker" "$WORK/bin/pg_isready"
(
    cd "$PIN_REPO"
    git init -q
    git config user.email test@example.invalid
    git config user.name test
    git commit --allow-empty -q -m init
)
PINNED_SHA="$(git -C "$PIN_REPO" rev-parse HEAD)"
PATH="$WORK/bin:$PATH" bash "$PIN_REPO/scripts/local-ci.sh" --run
assert_eq "$PINNED_SHA" "$(cat "$PIN_REPO/logs/local-ci/head.sha")" "local-ci changed the covered HEAD pin"
assert_eq PASS "$(cat "$PIN_REPO/logs/local-ci/result.txt")" "pinned fixture did not finish green"
[ -s "$PIN_REPO/logs/local-ci/run.id" ] || die "local-ci did not write a run id"
[ "$PINNED_SHA" != "$(git -C "$PIN_REPO" rev-parse HEAD)" ] || die "pin fixture did not move HEAD"

# The daemon queues a mid-run push, launches exactly once for the new SHA, and
# consumes a completed result without relaunching it.
WATCH_REPO="$WORK/watch-repo"
mkdir -p "$WATCH_REPO/scripts" "$WATCH_REPO/.wayfinder/handoffs" "$WATCH_REPO/status"
cp "$ROOT/scripts/wayfinder-loop.sh" "$WATCH_REPO/scripts/"
cat >"$WATCH_REPO/scripts/fake-local-ci.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
state="${LOCAL_CI_STATE_DIR:?}"
mkdir -p "$state"
printf '%s\n' "$(git rev-parse HEAD)" >"$state/head.sha"
printf 'PASS\n' >"$state/result.txt"
count="$(cat "$state/launches" 2>/dev/null || echo 0)"
printf '%s\n' "$((count + 1))" >"$state/launches"
EOF
cat >"$WORK/bin/opencode" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$WATCH_REPO/scripts/fake-local-ci.sh" "$WORK/bin/opencode"
(
    cd "$WATCH_REPO"
    git init -q
    git config user.email test@example.invalid
    git config user.name test
    git commit --allow-empty -q -m init
)

WATCH_STATE="$WATCH_REPO/status"
WATCH_LOOP="$WATCH_REPO/scripts/wayfinder-loop.sh"
watch_once() {
    local output=$1
    env \
        OPENCODE_BIN="$WORK/bin/opencode" \
        WAYFINDER_LOCAL_CI_DIR="$WATCH_STATE" \
        WAYFINDER_LOCAL_CI_SCRIPT="$WATCH_REPO/scripts/fake-local-ci.sh" \
        WAYFINDER_GH_BIN="$WORK/bin/gh" \
        WAYFINDER_GH_REPO=fixture/repo \
        WAYFINDER_MAP_ISSUE=533 \
        WAYFINDER_FRONTIER_ISSUE=901 \
        GH_CALLS="$WORK/gh-calls" \
        "$WATCH_LOOP" --local-ci-once >"$output" 2>&1
}

SHA_A="$(git -C "$WATCH_REPO" rev-parse HEAD)"
sleep 60 &
OLD_PID=$!
printf '%s\n' "$OLD_PID" >"$WATCH_STATE/pid"
printf '%s\n' "$SHA_A" >"$WATCH_STATE/head.sha"
printf 'RUNNING\n' >"$WATCH_STATE/status.txt"
: >"$WORK/gh-calls"
watch_once "$WORK/watch-a.log"
[ ! -f "$WATCH_STATE/launches" ] || die "daemon launched while the covering run was active"

git -C "$WATCH_REPO" commit --allow-empty -q -m pushed
SHA_B="$(git -C "$WATCH_REPO" rev-parse HEAD)"
watch_once "$WORK/watch-b.log"
grep -q 'queued a fresh run' "$WORK/watch-b.log" || die "mid-run HEAD move was not queued"
assert_eq "$SHA_A" "$(cat "$WATCH_STATE/head.sha")" "mid-run push rewrote the active run's SHA pin"
kill "$OLD_PID" 2>/dev/null || true
wait "$OLD_PID" 2>/dev/null || true
OLD_PID=""
watch_once "$WORK/watch-b-complete.log"
assert_eq 1 "$(cat "$WATCH_STATE/launches")" "queued SHA was not launched exactly once"
assert_eq "$SHA_B" "$(cat "$WATCH_STATE/head.sha")" "new run did not cover the pushed SHA"
assert_eq PASS "$(cat "$WATCH_STATE/result.txt")" "new run result was not consumed"
watch_once "$WORK/watch-b-repeat.log"
assert_eq 1 "$(cat "$WATCH_STATE/launches")" "completed SHA was relaunched"
grep -q "local_ci_verdicts=$SHA_B=PASS" "$WATCH_REPO/.wayfinder-loop.state" ||
    die "completed green SHA was not deduped in daemon state"

# A dry run against a fixture status directory plans a missing run and performs
# no launch or tracker write.
git -C "$WATCH_REPO" commit --allow-empty -q -m dry-run-head
SHA_DRY="$(git -C "$WATCH_REPO" rev-parse HEAD)"
DRY_STATE="$WATCH_REPO/dry-status"
DRY_OUTPUT="$WORK/dry-run.log"
env \
    OPENCODE_BIN="$WORK/bin/opencode" \
    WAYFINDER_DRY_RUN=1 \
    WAYFINDER_LOCAL_CI_DIR="$DRY_STATE" \
    WAYFINDER_LOCAL_CI_SCRIPT="$WATCH_REPO/scripts/missing-local-ci.sh" \
    WAYFINDER_GH_BIN="$WORK/bin/gh" \
    GH_CALLS="$WORK/gh-calls" \
    "$WATCH_LOOP" --local-ci-once >"$DRY_OUTPUT" 2>&1
grep -q "DRY-RUN: would launch local-ci for HEAD $SHA_DRY" "$DRY_OUTPUT" ||
    die "dry-run did not report the missing fixture run"
[ ! -d "$DRY_STATE" ] || die "dry-run created local-CI state"

# Recording gh fixture. It supports only the calls needed by the red path and
# returns a native map parent for the synthetic repair issue.
cat >"$WORK/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -u
printf '%s\n' "$*" >>"${GH_CALLS:?}"
if [ "${1:-}" = auth ] && [ "${2:-}" = status ]; then
    exit 0
fi
if [ "${1:-}" = issue ] && [ "${2:-}" = create ]; then
    printf 'https://github.com/fixture/repo/issues/900\n'
    exit 0
fi
if [ "${1:-}" = api ]; then
    endpoint=""
    has_jq=0
    for arg in "$@"; do
        case "$arg" in
            repos/*) endpoint="$arg" ;;
            --jq) has_jq=1 ;;
        esac
    done
    case "$endpoint" in
        'repos/fixture/repo/issues?state=all&per_page=100')
            printf '[]\n'
            ;;
        'repos/fixture/repo/issues/900')
            if [ "$has_jq" -eq 1 ]; then
                printf 'https://api.github.com/repos/fixture/repo/issues/533\n'
            else
                printf '{"id":900001,"number":900,"state":"open","parent_issue_url":"https://api.github.com/repos/fixture/repo/issues/533"}\n'
            fi
            ;;
        'repos/fixture/repo/issues/901/dependencies/blocked_by')
            printf '[]\n'
            ;;
        *)
            printf '{}\n'
            ;;
    esac
    exit 0
fi
exit 0
EOF
chmod +x "$WORK/bin/gh"

git -C "$WATCH_REPO" commit --allow-empty -q -m red-head
SHA_RED="$(git -C "$WATCH_REPO" rev-parse HEAD)"
printf '%s\n' "$SHA_RED" >"$WATCH_STATE/head.sha"
printf 'FAIL\n' >"$WATCH_STATE/result.txt"
: >"$WORK/gh-calls"
watch_once "$WORK/watch-red.log"
assert_eq 1 "$(grep -c '^issue create' "$WORK/gh-calls")" "red verdict created duplicate/zero repair tickets"
grep -q 'dependencies/blocked_by' "$WORK/gh-calls" || die "red verdict did not add a frontier dependency"
grep -q "local_ci_repair_issues=$SHA_RED:900" "$WATCH_REPO/.wayfinder-loop.state" ||
    die "red SHA-to-repair mapping was not persisted"

watch_once "$WORK/watch-red-repeat.log"
assert_eq 1 "$(grep -c '^issue create' "$WORK/gh-calls")" "same red SHA created a second repair ticket"
grep -q "$SHA_RED=FAIL" "$WATCH_REPO/.wayfinder-loop.state" ||
    die "red verdict was not deduped per SHA"

echo "test-wayfinder-local-ci-watch: OK"

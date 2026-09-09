#!/usr/bin/env bash
# Script-level contract test for the hosted-CI repair watch (ref #627, map #697 #739).
# The daemon reconciles HEAD against hosted check-runs and mints at most one
# durable repair ticket per red SHA — nothing is ever launched locally — but
# only when WAYFINDER_CI_REPAIR=on. By default (ref #652) the watch is a no-op:
# no polling, no tickets, no verdicts. A red baseline no longer stops the
# normal frontier (serialized stop-the-line is gone): the repair issue is the
# maintenance worker's task for the chief --spawn-maintenance lane, unrelated
# ticket workers continue in isolated workspaces, and canonical integration
# stays gated until the repair lands. Fixture gh serves per-SHA check-runs
# payloads; no Gradle, database, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d /tmp/opencode/wayfinder-ci-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT

die() {
    printf 'test-wayfinder-ci-watch: %s\n' "$*" >&2
    exit 1
}

assert_eq() {
    local expected=$1 actual=$2 message=${3:-values differ}
    [ "$expected" = "$actual" ] || die "$message (expected '$expected', got '$actual')"
}

WATCH_REPO="$WORK/watch-repo"
mkdir -p "$WATCH_REPO/tools/wayfinder" "$WATCH_REPO/scripts" "$WATCH_REPO/.wayfinder/handoffs" "$WORK/bin"
cp "$ROOT/tools/wayfinder/wayfinder-loop.sh" "$WATCH_REPO/tools/wayfinder/"
cp "$ROOT/scripts/wayfinder-loop.sh" "$WATCH_REPO/scripts/"
# Compat wrapper must delegate to the canonical implementation.
grep -q 'tools/wayfinder/wayfinder-loop.sh' "$WATCH_REPO/scripts/wayfinder-loop.sh" \
    || die "compat wrapper does not delegate to the canonical implementation"

# Recording gh fixture: check-runs payloads come from $WORK/checks-<sha>.json
# (missing file = empty run list). Only the red-path tracker calls are
# supported; a native map parent is returned for the synthetic repair issue.
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
        repos/fixture/repo/commits/*/check-runs*)
            sha="${endpoint#repos/fixture/repo/commits/}"
            sha="${sha%%/*}"
            if [ -f "$WORK_FIXTURES/checks-$sha.json" ]; then
                cat "$WORK_FIXTURES/checks-$sha.json"
            else
                printf '{"total_count":0,"check_runs":[]}\n'
            fi
            ;;
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
        *)
            printf '{}\n'
            ;;
    esac
    exit 0
fi
exit 0
EOF
cat >"$WORK/bin/opencode" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$WORK/bin/gh" "$WORK/bin/opencode"
(
    cd "$WATCH_REPO"
    git init -q
    git config user.email test@example.invalid
    git config user.name test
    git commit --allow-empty -q -m init
)

WATCH_LOOP="$WATCH_REPO/tools/wayfinder/wayfinder-loop.sh"
watch_once() {
    local output=$1
    env \
        OPENCODE_BIN="$WORK/bin/opencode" \
        WAYFINDER_CI_REPAIR=on \
        WAYFINDER_GH_BIN="$WORK/bin/gh" \
        WAYFINDER_GH_REPO=fixture/repo \
        WAYFINDER_MAP_ISSUE=533 \
        GH_CALLS="$WORK/gh-calls" \
        WORK_FIXTURES="$WORK" \
        "$WATCH_LOOP" --ci-watch-once >"$output" 2>&1
}

checks_for() {
    printf '%s' "$1" >"$WORK/checks-$2.json"
}
no_writes() {
    if grep -q '^issue create' "$WORK/gh-calls" 2>/dev/null; then
        die "unexpected repair ticket: $(cat "$WORK/gh-calls")"
    fi
    if grep -q -- '--method POST' "$WORK/gh-calls" 2>/dev/null; then
        die "unexpected tracker write: $(cat "$WORK/gh-calls")"
    fi
}

# PENDING: runs still in flight — no ticket, no recorded verdict.
git -C "$WATCH_REPO" commit --allow-empty -q -m pending-head
SHA_PENDING="$(git -C "$WATCH_REPO" rev-parse HEAD)"
checks_for '{"total_count":2,"check_runs":[{"name":"quality","conclusion":"success"},{"name":"compose","conclusion":null}]}' "$SHA_PENDING"
: >"$WORK/gh-calls"
watch_once "$WORK/watch-pending.log"
if grep -q 'issue create' "$WORK/gh-calls"; then die "pending HEAD minted a repair ticket"; fi
grep -q "ci_verdicts=$SHA_PENDING" "$WATCH_REPO/.wayfinder-loop.state" 2>/dev/null &&
    die "pending HEAD recorded a verdict"

# GREEN: all concluded success — verdict recorded, still no ticket.
git -C "$WATCH_REPO" commit --allow-empty -q -m green-head
SHA_GREEN="$(git -C "$WATCH_REPO" rev-parse HEAD)"
checks_for '{"total_count":2,"check_runs":[{"name":"quality","conclusion":"success"},{"name":"compose","conclusion":"success"}]}' "$SHA_GREEN"
: >"$WORK/gh-calls"
watch_once "$WORK/watch-green.log"
no_writes
grep -q "ci_verdicts=$SHA_GREEN=PASS" "$WATCH_REPO/.wayfinder-loop.state" ||
    die "green HEAD was not recorded"
watch_once "$WORK/watch-green-repeat.log"
grep -q "ci_verdicts=$SHA_GREEN=PASS" "$WATCH_REPO/.wayfinder-loop.state" ||
    die "green verdict did not survive a repeat poll"

# UNKNOWN: hosted CI never ran this HEAD (path-scoped-out push) — silence.
git -C "$WATCH_REPO" commit --allow-empty -q -m docs-head
SHA_DOCS="$(git -C "$WATCH_REPO" rev-parse HEAD)"
: >"$WORK/gh-calls"
watch_once "$WORK/watch-unknown.log"
no_writes
grep -q "ci_verdicts=$SHA_DOCS" "$WATCH_REPO/.wayfinder-loop.state" 2>/dev/null &&
    die "unran HEAD recorded a verdict"

# RED: one failing check — exactly one durable repair ticket, persisted mapping
# and dedupe, but NO frontier block: implementation continues in isolated
# workspaces while the repair issue waits for the chief maintenance lane.
git -C "$WATCH_REPO" commit --allow-empty -q -m red-head
SHA_RED="$(git -C "$WATCH_REPO" rev-parse HEAD)"
checks_for '{"total_count":2,"check_runs":[{"name":"quality","conclusion":"failure"},{"name":"compose","conclusion":"success"}]}' "$SHA_RED"
: >"$WORK/gh-calls"
watch_once "$WORK/watch-red.log"
assert_eq 1 "$(grep -c '^issue create' "$WORK/gh-calls")" "red verdict created duplicate/zero repair tickets"
if grep -q 'dependencies/blocked_by' "$WORK/gh-calls"; then die "red verdict blocked the frontier (serialized stop-the-line is gone — see map #697 #739)"; fi
grep -q "ready for chief maintenance dispatch" "$WORK/watch-red.log" ||
    die "red verdict did not direct the repair to the maintenance lane"
grep -q "may continue in isolated workspaces" "$WORK/watch-red.log" ||
    die "red verdict did not record the implementation-continues contract"
grep -q "ci_repair_issues=$SHA_RED:900" "$WATCH_REPO/.wayfinder-loop.state" ||
    die "red SHA-to-repair mapping was not persisted"
grep -q "$SHA_RED=FAIL" "$WATCH_REPO/.wayfinder-loop.state" ||
    die "red verdict was not deduped per SHA"

watch_once "$WORK/watch-red-repeat.log"
assert_eq 1 "$(grep -c '^issue create' "$WORK/gh-calls")" "same red SHA created a second repair ticket"
if grep -q 'dependencies/blocked_by' "$WORK/gh-calls"; then die "repeat poll blocked the frontier"; fi

# DRY-RUN on a fresh red HEAD plans the ticket and writes nothing.
git -C "$WATCH_REPO" commit --allow-empty -q -m dry-red-head
SHA_DRY="$(git -C "$WATCH_REPO" rev-parse HEAD)"
checks_for '{"total_count":1,"check_runs":[{"name":"quality","conclusion":"cancelled"}]}' "$SHA_DRY"
: >"$WORK/gh-calls"
env \
    OPENCODE_BIN="$WORK/bin/opencode" \
    WAYFINDER_DRY_RUN=1 \
    WAYFINDER_CI_REPAIR=on \
    WAYFINDER_GH_BIN="$WORK/bin/gh" \
    WAYFINDER_GH_REPO=fixture/repo \
    WAYFINDER_MAP_ISSUE=533 \
    GH_CALLS="$WORK/gh-calls" \
    WORK_FIXTURES="$WORK" \
    "$WATCH_REPO/scripts/wayfinder-loop.sh" --ci-watch-once >"$WORK/dry-run.log" 2>&1
grep -q "DRY-RUN: hosted-CI verdict FAIL for HEAD $SHA_DRY" "$WORK/dry-run.log" ||
    die "dry-run did not report the red fixture HEAD"
if grep -q 'issue create' "$WORK/gh-calls"; then die "dry-run wrote a repair ticket"; fi
grep -q "ci_verdicts=$SHA_DRY" "$WATCH_REPO/.wayfinder-loop.state" 2>/dev/null &&
    die "dry-run recorded a verdict"

# Disabled by default (ref #652): even a RED HEAD mints nothing and records
# nothing unless WAYFINDER_CI_REPAIR=on.
git -C "$WATCH_REPO" commit --allow-empty -q -m off-red-head
SHA_OFF="$(git -C "$WATCH_REPO" rev-parse HEAD)"
checks_for '{"total_count":1,"check_runs":[{"name":"quality","conclusion":"failure"}]}' "$SHA_OFF"
: >"$WORK/gh-calls"
env \
    OPENCODE_BIN="$WORK/bin/opencode" \
    WAYFINDER_GH_BIN="$WORK/bin/gh" \
    WAYFINDER_GH_REPO=fixture/repo \
    WAYFINDER_MAP_ISSUE=533 \
    GH_CALLS="$WORK/gh-calls" \
    WORK_FIXTURES="$WORK" \
    "$WATCH_LOOP" --ci-watch-once >"$WORK/watch-off.log" 2>&1
no_writes
grep -q "ci_verdicts=$SHA_OFF" "$WATCH_REPO/.wayfinder-loop.state" 2>/dev/null &&
    die "disabled watch recorded a verdict"
grep -q "ci_repair_issues=$SHA_OFF" "$WATCH_REPO/.wayfinder-loop.state" 2>/dev/null &&
    die "disabled watch recorded a repair mapping"

echo "test-wayfinder-ci-watch: OK"

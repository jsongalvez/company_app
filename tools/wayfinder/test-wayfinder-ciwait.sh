#!/usr/bin/env bash
# Script-level contract test for the CI-wait spawn hold (map #668 / ticket
# #695 pending-packet spin class). A packet carrying
# `<!-- wayfinder-ci-wait: <full-sha> -->` holds its spawn while hosted CI is
# still in flight for that SHA instead of burning a full-context worker that
# rehydrates, re-reconciles PENDING, and mints another numbered packet.
# Fixture gh serves per-SHA check-runs payloads; the `--ciwait-probe`
# one-shot exercises the gate without creating a session. No Gradle,
# database, or network.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d /tmp/opencode/wayfinder-ciwait-test.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT

die() {
    printf 'test-wayfinder-ciwait: %s\n' "$*" >&2
    exit 1
}

CIWAIT_REPO="$WORK/ciwait-repo"
mkdir -p "$CIWAIT_REPO/tools/wayfinder" "$CIWAIT_REPO/.wayfinder/handoffs" "$WORK/bin"
cp "$ROOT/tools/wayfinder/wayfinder-loop.sh" "$CIWAIT_REPO/tools/wayfinder/"
(
    cd "$CIWAIT_REPO"
    git init -q
    git config user.email test@example.invalid
    git config user.name test
    git commit --allow-empty -q -m init
)

# Recording gh fixture: check-runs payloads come from $WORK/checks-<sha>.json
# (missing file = empty run list = UNKNOWN).
cat >"$WORK/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -u
printf '%s\n' "$*" >>"${GH_CALLS:?}"
if [ "${1:-}" = api ]; then
    endpoint=""
    for arg in "$@"; do
        case "$arg" in
            repos/*) endpoint="$arg" ;;
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

CIWAIT_LOOP="$CIWAIT_REPO/tools/wayfinder/wayfinder-loop.sh"
probe() {
    local doc=$1 extra=${2:-}
    env \
        OPENCODE_BIN="$WORK/bin/opencode" \
        WAYFINDER_GH_BIN="$WORK/bin/gh" \
        WAYFINDER_GH_REPO=fixture/repo \
        ${extra:-} \
        GH_CALLS="$WORK/gh-calls" \
        WORK_FIXTURES="$WORK" \
        "$CIWAIT_LOOP" --ciwait-probe "$doc" 2>"$WORK/probe-err.log" | tail -n 1 || die "probe failed for $doc: $(cat "$WORK/probe-err.log")"
}

checks_for() {
    printf '%s' "$1" >"$WORK/checks-$2.json"
}

# Fixed SHAs keep the fixture independent of the repo's own commits.
SHA_PENDING="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
SHA_GREEN="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
SHA_RED="cccccccccccccccccccccccccccccccccccccccc"
SHA_UNKNOWN="dddddddddddddddddddddddddddddddddddddddd"
checks_for '{"total_count":2,"check_runs":[{"name":"quality","conclusion":null},{"name":"compose","conclusion":"success"}]}' "$SHA_PENDING"
checks_for '{"total_count":2,"check_runs":[{"name":"quality","conclusion":"success"},{"name":"compose","conclusion":"success"}]}' "$SHA_GREEN"
checks_for '{"total_count":2,"check_runs":[{"name":"quality","conclusion":"failure"},{"name":"compose","conclusion":"success"}]}' "$SHA_RED"

hold_doc() {
    printf '<!-- wayfinder-ci-wait: %s -->\n# hold packet\n' "$1" >"$CIWAIT_REPO/.wayfinder/handoffs/wayfinder-1-hold-handoff.md"
}

# PENDING: hold, and the hold polls check-runs exactly once (CIWAIT_ONCE).
hold_doc "$SHA_PENDING"
: >"$WORK/gh-calls"
[ "$(probe wayfinder-1-hold-handoff.md)" = "HOLD" ] || die "pending hold did not HOLD"
grep -q "commits/$SHA_PENDING/check-runs" "$WORK/gh-calls" || die "pending hold polled no check-runs"

# GREEN: concluded success wakes exactly one session.
hold_doc "$SHA_GREEN"
: >"$WORK/gh-calls"
[ "$(probe wayfinder-1-hold-handoff.md)" = "PROCEED" ] || die "green hold did not PROCEED"

# RED: concluded failure also wakes (the session repairs).
hold_doc "$SHA_RED"
[ "$(probe wayfinder-1-hold-handoff.md)" = "PROCEED" ] || die "red hold did not PROCEED"

# UNKNOWN: CI never ran this HEAD — nothing to await, proceed.
hold_doc "$SHA_UNKNOWN"
[ "$(probe wayfinder-1-hold-handoff.md)" = "PROCEED" ] || die "unknown hold did not PROCEED"

# Unmarked packet: proceed with zero hosted polling.
printf '# normal packet, no marker\n' >"$CIWAIT_REPO/.wayfinder/handoffs/wayfinder-2-work-handoff.md"
: >"$WORK/gh-calls"
[ "$(probe wayfinder-2-work-handoff.md)" = "PROCEED" ] || die "unmarked packet did not PROCEED"
if grep -q "check-runs" "$WORK/gh-calls"; then die "unmarked packet polled hosted CI"; fi

# Preemption: pending hold plus a newer actionable packet — PREEMPT, not HOLD.
hold_doc "$SHA_PENDING"
[ "$(probe wayfinder-1-hold-handoff.md)" = "PREEMPT" ] || die "hold beside actionable packet did not PREEMPT"
rm "$CIWAIT_REPO/.wayfinder/handoffs/wayfinder-2-work-handoff.md"

# A second hold never preempts the first (no hold-vs-hold flapping).
printf '<!-- wayfinder-ci-wait: %s -->\n# second hold\n' "$SHA_GREEN" >"$CIWAIT_REPO/.wayfinder/handoffs/wayfinder-3-hold2-handoff.md"
[ "$(probe wayfinder-1-hold-handoff.md)" = "HOLD" ] || die "hold-vs-hold flapped to PREEMPT"
rm "$CIWAIT_REPO/.wayfinder/handoffs/wayfinder-3-hold2-handoff.md"

# Disabled (WAYFINDER_CIWAIT=off): proceed with zero hosted polling.
hold_doc "$SHA_PENDING"
: >"$WORK/gh-calls"
[ "$(probe wayfinder-1-hold-handoff.md WAYFINDER_CIWAIT=off)" = "PROCEED" ] || die "disabled gate did not PROCEED"
if grep -q "check-runs" "$WORK/gh-calls"; then die "disabled gate polled hosted CI"; fi

# Unusable gh fails open: the session-side reconcile stays the authority.
[ "$(probe wayfinder-1-hold-handoff.md WAYFINDER_GH_BIN=/bin/false)" = "PROCEED" ] || die "gh outage did not fail open"

# Malformed marker (short sha): not a hold, proceed without polling.
printf '<!-- wayfinder-ci-wait: abc123 -->\n# bad marker\n' >"$CIWAIT_REPO/.wayfinder/handoffs/wayfinder-4-bad-handoff.md"
: >"$WORK/gh-calls"
[ "$(probe wayfinder-4-bad-handoff.md)" = "PROCEED" ] || die "malformed marker did not PROCEED"
if grep -q "check-runs" "$WORK/gh-calls"; then die "malformed marker polled hosted CI"; fi

echo "test-wayfinder-ciwait: OK"

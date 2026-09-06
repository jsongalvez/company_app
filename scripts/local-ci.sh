#!/usr/bin/env bash
# Local full-CI replication runner (#341).
#
# Replicates the hosted quality.yml gate set on this machine, detached, so a push
# can be followed by broad verification without waiting and without spending hosted
# Actions minutes. Opt-in diagnostic — never an integration gate (map #329/#333).
#
# Usage:
#   bash scripts/local-ci.sh           # launch the sweep detached, return immediately
#   bash scripts/local-ci.sh --status  # print per-gate status of the last/current run
#   bash scripts/local-ci.sh --run     # internal: execute the gates in foreground
#
# State lives under logs/local-ci/ (gitignored): run.log, status.txt, result.txt,
# head.sha (the commit covered by the run), run.id, and pid.

set -u

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${LOCAL_CI_STATE_DIR:-$REPO/logs/local-ci}"
RUN_LOG="$STATE_DIR/run.log"
STATUS_FILE="$STATE_DIR/status.txt"
RESULT_FILE="$STATE_DIR/result.txt"
PID_FILE="$STATE_DIR/pid"
HEAD_FILE="$STATE_DIR/head.sha"
RUN_ID_FILE="$STATE_DIR/run.id"
LOCK_FILE="$STATE_DIR/lock"

cd "$REPO" || exit 1

QUALITY_TASKS=':backend:detekt :backend:ktlintCheck :backend:test
:shared:detektMetadataCommonMain
:shared:detektJvmMain :shared:detektJvmTest
:shared:detektAndroidDebug :shared:detektAndroidDebugUnitTest
:shared:detektIosArm64Main :shared:detektIosArm64Test
:shared:detektIosSimulatorArm64Main :shared:detektIosSimulatorArm64Test
:composeApp:detektDesktopTest
:composeApp:detektAndroidDebugUnitTest
:composeApp:detektIosArm64Test
:composeApp:detektIosSimulatorArm64Test
:composeApp:desktopTest
:composeApp:detektMetadataCommonMain :composeApp:detektDesktopMain
:composeApp:detektAndroidDebug :composeApp:detektIosArm64Main
:composeApp:detektIosSimulatorArm64Main
:shared:compileKotlinJvm :shared:jvmTest'

record() { # record <gate> <PASS|FAIL|SKIP|RUNNING> [note]
    local gate=$1 state=$2 note=${3:-}
    printf '%s=%s%s\n' "$gate" "$state" "${note:+ ($note)}" >>"$STATUS_FILE"
}

current_head() {
    git -C "$REPO" rev-parse HEAD 2>/dev/null
}

valid_sha() {
    [[ "$1" =~ ^[[:xdigit:]]{40,64}$ ]]
}

write_atomic() {
    local file=$1 value=$2
    local tmp="${file}.tmp.$$"
    printf '%s\n' "$value" >"$tmp"
    mv -f "$tmp" "$file"
}

begin_run() {
    local sha="${LOCAL_CI_HEAD_SHA:-}" run_id="${LOCAL_CI_RUN_ID:-}"
    mkdir -p "$STATE_DIR"

    if [ -z "$sha" ]; then
        sha="$(current_head)" || {
            printf 'local-ci: cannot determine HEAD\n' >&2
            return 1
        }
    fi
    valid_sha "$sha" || {
        printf 'local-ci: invalid HEAD sha: %s\n' "$sha" >&2
        return 1
    }
    [ -n "$run_id" ] || run_id="$(date +%s)-$$"

    # These pins are written before any expensive gate starts. A later push must
    # never rewrite the mapping for this run; the loop will queue the new HEAD.
    write_atomic "$HEAD_FILE" "$sha"
    write_atomic "$RUN_ID_FILE" "$run_id"
    : >"$STATUS_FILE"
    : >"$RESULT_FILE"
    : >"$RUN_LOG"
}

android_sdk_available() {
    [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ] && return 0
    [ -f local.properties ] && grep -q '^sdk.dir=' local.properties && return 0
    return 1
}

postgres_up() {
    docker compose -f docker/docker-compose.yml up -d >/dev/null 2>&1 || return 1
    local host port
    host=$(grep -E '^DB_HOST=' .env | cut -d= -f2)
    port=$(grep -E '^DB_PORT=' .env | cut -d= -f2)
    for _ in $(seq 1 30); do
        pg_isready -h "$host" -p "$port" >/dev/null 2>&1 && return 0
        sleep 1
    done
    return 1
}

run_gates() {
    begin_run || return 1

    if postgres_up; then
        record postgres PASS
    else
        record postgres FAIL "docker compose up / pg_isready failed — DB-backed gates will fail"
    fi

    # Gate: quality — verbatim hosted invocation (quality.yml job 1).
    echo "./gradlew $QUALITY_TASKS -PwarningsAsErrors=true" >>"$RUN_LOG"
    if ./gradlew $QUALITY_TASKS -PwarningsAsErrors=true >>"$RUN_LOG" 2>&1; then
        record quality PASS
    else
        record quality FAIL "see $RUN_LOG"
    fi

    # Gate: cleanliness — backend workers use owned test_w_* schemas (#493), so the
    # public-table check no longer evidences backend tests (lifecycle test owns it).
    # Kept scripts stay for k6/manual public-DB cleanup; no backend gate here.

    # Gate: openapi — mirrors the hosted step (#495 Gradle contract gate).
    if ./gradlew :backend:verifyOpenApiContract -PwarningsAsErrors=true >>"$RUN_LOG" 2>&1; then
        record openapi PASS
    else
        record openapi FAIL "see $RUN_LOG"
    fi

    # Gates: compose-compile matrix (quality.yml job 2).
    if android_sdk_available; then
        if ./gradlew :composeApp:compileDebugKotlinAndroid -PwarningsAsErrors=true >>"$RUN_LOG" 2>&1; then
            record compose-android PASS
        else
            record compose-android FAIL "see $RUN_LOG"
        fi
    else
        record compose-android SKIP "no Android SDK detected"
    fi

    if ./gradlew :composeApp:compileKotlinDesktop -PwarningsAsErrors=true >>"$RUN_LOG" 2>&1; then
        record compose-desktop PASS
    else
        record compose-desktop FAIL "see $RUN_LOG"
    fi

    if grep -q '=FAIL' "$STATUS_FILE"; then
        write_atomic "$RESULT_FILE" FAIL
    else
        write_atomic "$RESULT_FILE" PASS
    fi
}

show_status() {
    if [ ! -f "$STATUS_FILE" ]; then
        echo "no run found — launch with: bash scripts/local-ci.sh"
        exit 1
    fi
    cat "$STATUS_FILE"
    if [ -f "$HEAD_FILE" ]; then
        echo "covered HEAD: $(cat "$HEAD_FILE") (run $(cat "$RUN_ID_FILE" 2>/dev/null || echo unknown))"
    else
        echo "covered HEAD: unknown (legacy run state)"
    fi
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "overall: RUNNING (pid $(cat "$PID_FILE"), log: $RUN_LOG)"
    elif [ -f "$RESULT_FILE" ]; then
        echo "overall: $(cat "$RESULT_FILE")"
    else
        echo "overall: INCOMPLETE (runner died? log: $RUN_LOG)"
    fi
}

case "${1:-}" in
    --status)
        show_status
        ;;
    --run)
        run_gates
        ;;
    *)
        mkdir -p "$STATE_DIR"
        # Serialize the launch/check/pin window. The daemon has its own flock,
        # but an operator may also invoke this wrapper manually.
        exec 8>"$LOCK_FILE"
        flock -n 8 || {
            echo "already launching (another local-ci invocation holds $LOCK_FILE)"
            exit 1
        }
        if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
            echo "already running (pid $(cat "$PID_FILE")) — bash scripts/local-ci.sh --status"
            exit 1
        fi
        sha="$(current_head)" || {
            echo "cannot launch: unable to determine HEAD" >&2
            exit 1
        }
        valid_sha "$sha" || {
            echo "cannot launch: invalid HEAD sha: $sha" >&2
            exit 1
        }
        run_id="$(date +%s)-$$"
        # Pin and invalidate the previous verdict before the child is detached.
        # The child receives both values so a push between fork and exec cannot
        # change what this run claims to verify.
        write_atomic "$HEAD_FILE" "$sha"
        write_atomic "$RUN_ID_FILE" "$run_id"
        : >"$RUN_LOG"
        : >"$STATUS_FILE"
        : >"$RESULT_FILE"
        nohup env LOCAL_CI_HEAD_SHA="$sha" LOCAL_CI_RUN_ID="$run_id" \
            LOCAL_CI_STATE_DIR="$STATE_DIR" bash "$0" --run </dev/null >>"$RUN_LOG" 2>&1 &
        echo $! >"$PID_FILE"
        disown
        echo "launched (pid $(cat "$PID_FILE")) — check with: bash scripts/local-ci.sh --status"
        ;;
esac

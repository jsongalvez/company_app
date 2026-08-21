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
# State lives under logs/local-ci/ (gitignored): run.log, status.txt, result.txt.

set -u

STATE_DIR="logs/local-ci"
RUN_LOG="$STATE_DIR/run.log"
STATUS_FILE="$STATE_DIR/status.txt"
RESULT_FILE="$STATE_DIR/result.txt"
PID_FILE="$STATE_DIR/pid"

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
:shared:compileKotlinJvm :shared:jvmTest -x :backend:publishOpenApiSpec'

record() { # record <gate> <PASS|FAIL|SKIP|RUNNING> [note]
    local gate=$1 state=$2 note=${3:-}
    printf '%s=%s%s\n' "$gate" "$state" "${note:+ ($note)}" >>"$STATUS_FILE"
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
    mkdir -p "$STATE_DIR"
    : >"$STATUS_FILE"

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

    # Gate: cleanliness — mirrors the hosted step.
    if bash scripts/check-test-cleanliness.sh >>"$RUN_LOG" 2>&1; then
        record cleanliness PASS
    else
        record cleanliness FAIL "test DB contaminated — bash scripts/clean-test-db.sh, then rerun"
    fi

    # Gate: openapi — mirrors the hosted step.
    if bash scripts/check-openapi-spec.sh >>"$RUN_LOG" 2>&1; then
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
        echo FAIL >"$RESULT_FILE"
    else
        echo PASS >"$RESULT_FILE"
    fi
}

show_status() {
    if [ ! -f "$STATUS_FILE" ]; then
        echo "no run found — launch with: bash scripts/local-ci.sh"
        exit 1
    fi
    cat "$STATUS_FILE"
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
        if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
            echo "already running (pid $(cat "$PID_FILE")) — bash scripts/local-ci.sh --status"
            exit 1
        fi
        : >"$RUN_LOG"
        nohup bash "$0" --run </dev/null >>"$RUN_LOG" 2>&1 &
        echo $! >"$PID_FILE"
        disown
        echo "launched (pid $(cat "$PID_FILE")) — check with: bash scripts/local-ci.sh --status"
        ;;
esac

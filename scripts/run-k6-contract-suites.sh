#!/bin/bash
# Run every k6 contract suite against a disposable test-database backend.
#
# Used by CI (.github/workflows/k6.yml) and runnable locally as the k6 gate:
#
#   bash scripts/run-k6-contract-suites.sh
#
# The backend boots against the TEST database (never the application database)
# with deterministic seed data: DevSeeder creates the owner user and the
# "K6 Fixture Branch" fixtures on startup when TEST_USERNAME/TEST_PASSWORD are
# set. An ephemeral capability-less user is registered through the public API
# for the authz suite's insufficient-capability (403) contract.
#
# Suite order matters: remittance-race-test requires a fresh clock-in (strict
# HTTP 201) while every other suite tolerates 409 (already clocked in), and
# clock-in is one-active-per-user — so the race suite runs first.
#
# Failure-safe cleanup: an EXIT trap stops the backend and restores the test
# database cleanliness invariant even when a suite fails.
#
# Credentials below mirror the disposable CI environment (.env.example,
# quality.yml); override any of them for local runs.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-company_user}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-company_password}"
TEST_DB_NAME="${TEST_DB_NAME:-company_app_test}"
# Force the backend onto the test database — never the application database.
POSTGRES_DB="$TEST_DB_NAME"
TEST_USERNAME="${TEST_USERNAME:-owner}"
TEST_PASSWORD="${TEST_PASSWORD:-pass}"
LIMITED_USERNAME="${LIMITED_USERNAME:-limited}"
LIMITED_PASSWORD="${LIMITED_PASSWORD:-password}"
JWT_SECRET="${JWT_SECRET:-ci-secret-must-be-at-least-64-characters-long-for-tests-0123456789}"
JWT_ISSUER="${JWT_ISSUER:-company-app-ci}"
JWT_AUDIENCE="${JWT_AUDIENCE:-company-app-ci}"
AUTH_DUMMY_PASSWORD="${AUTH_DUMMY_PASSWORD:-ci-dummy-password}"
APP_PORT="${APP_PORT:-8180}"
API_BASE_URL="http://localhost:${APP_PORT}"

export DB_HOST DB_PORT POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB \
    TEST_DB_NAME TEST_USERNAME TEST_PASSWORD \
    JWT_SECRET JWT_ISSUER JWT_AUDIENCE AUTH_DUMMY_PASSWORD \
    APP_PORT API_BASE_URL

BOOT_LOG=/tmp/company-app-k6-boot.log
K6_LOG=/tmp/company-app-k6.log
REGISTER_LOG=/tmp/company-app-k6-register.json
APP_PID=""

# Refuse to run when something already listens on the app port — the health
# check below would otherwise accept a foreign backend as "ready".
if (exec 3<>"/dev/tcp/127.0.0.1/${APP_PORT}") 2>/dev/null; then
    exec 3>&- 3<&- || true
    echo "ERROR: port $APP_PORT is already in use; pick another APP_PORT."
    exit 1
fi

cleanup() {
    local status=$?
    trap - EXIT
    if [ -n "$APP_PID" ]; then
        # Kill the whole process group (setsid leader + gradle + backend JVM).
        kill -- -"$APP_PID" 2>/dev/null || kill "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
    # Restore the cleanliness invariant even when a suite failed.
    if ! bash scripts/clean-test-db.sh 2>&1 | tail -3; then
        echo "ERROR: test database cleanup failed after k6 run."
        status=1
    fi
    exit "$status"
}
trap cleanup EXIT

echo "Building and starting backend on test DB '$POSTGRES_DB' (port $APP_PORT)..."
# Own process group so cleanup can take down gradle + the backend JVM together.
setsid ./gradlew :backend:run --no-daemon > "$BOOT_LOG" 2>&1 &
APP_PID=$!

# Cold CI Gradle builds can take longer than a minute before the app listens.
for attempt in $(seq 1 300); do
    if curl --fail --silent "${API_BASE_URL}/health" >/dev/null; then
        break
    fi
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        echo "ERROR: backend died during startup."
        tail -50 "$BOOT_LOG"
        exit 1
    fi
    sleep 2
done
curl --fail --silent "${API_BASE_URL}/health" >/dev/null || {
    echo "ERROR: backend did not become healthy."
    tail -50 "$BOOT_LOG"
    exit 1
}
echo "Backend is healthy."

# Ephemeral capability-less user for the authz 403 contract. 201 = created,
# 409 = leftover from an earlier run against the same database — both fine.
register_status=$(curl -s -o "$REGISTER_LOG" -w "%{http_code}" \
    -X POST "${API_BASE_URL}/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${LIMITED_USERNAME}\",\"password\":\"${LIMITED_PASSWORD}\",\"email\":\"${LIMITED_USERNAME}@k6.invalid\",\"displayName\":\"K6 Limited\"}")
case "$register_status" in
    201 | 409) echo "Limited user ready (HTTP $register_status)." ;;
    *)
        echo "ERROR: limited-user registration failed (HTTP $register_status)."
        cat "$REGISTER_LOG"
        exit 1
        ;;
esac

: > "$K6_LOG"
# Run every suite even when one fails; report the first failure's exit code.
overall=0
for suite in remittance-race-test baseline authz-test concurrency-test full-suite; do
    echo "--- k6 suite: $suite ---"
    set +e
    set -o pipefail
    k6 run "tests/k6/$suite.js" --quiet 2>&1 | tee -a "$K6_LOG"
    suite_status=${PIPESTATUS[0]}
    set +o pipefail
    set -e
    if [ "$suite_status" -ne 0 ]; then
        echo "k6 suite FAILED: $suite (exit $suite_status)"
        [ "$overall" -ne 0 ] || overall="$suite_status"
    else
        echo "k6 suite passed: $suite"
    fi
done

if [ "$overall" -eq 0 ]; then
    echo "All k6 contract suites passed."
else
    echo "--- k6 summary (tail) ---"
    tail -30 "$K6_LOG" || true
fi
exit "$overall"

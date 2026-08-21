#!/bin/bash
# Shared shell library for project scripts and git hooks.
# Source this file at the top of each script:
#   source "$ROOT_DIR/scripts/lib/common.sh"
# Caller must set ROOT_DIR before sourcing, or call ensure_root_dir afterwards.

# --- log TAG MSG ---
# Prints a timestamped log line with the given tag.
log() {
    local tag="$1"; shift
    echo "$(date '+%H:%M:%S') [$tag] $*"
}

# --- ensure_root_dir ---
# Sets ROOT_DIR to the git repo root and cd's there.
# Idempotent: if ROOT_DIR is already set and valid, does nothing.
ensure_root_dir() {
    if [ -z "${ROOT_DIR:-}" ] || [ ! -d "$ROOT_DIR" ]; then
        ROOT_DIR="$(git rev-parse --show-toplevel)"
    fi
    cd "$ROOT_DIR"
}

# --- source_env ---
# Sources .env from ROOT_DIR if it exists. Silent no-op when missing.
source_env() {
    ensure_root_dir
    source .env 2>/dev/null || true
}

# --- test_db_name ---
# Returns explicitly selected test DB, or derives one from the application DB.
test_db_name() {
    if [ -n "${TEST_DB_NAME:-}" ]; then
        printf '%s\n' "$TEST_DB_NAME"
    else
        printf '%s_test\n' "${POSTGRES_DB:?POSTGRES_DB is required}"
    fi
}

# --- test_data_tables USER DATABASE ---
# Prints newline-delimited non-seed base tables. Discovery failure is fatal to
# callers because an unreadable database must never look clean.
test_db_psql() {
    local db_user="$1"
    local db_name="$2"
    shift 2
    if [ -n "${TEST_DB_CONTAINER:-}" ] || ! command -v psql >/dev/null 2>&1; then
        docker exec "$TEST_DB_CONTAINER" psql -U "$db_user" -d "$db_name" "$@"
    else
        PGPASSWORD="${POSTGRES_PASSWORD:-}" psql \
            -h "${DB_HOST:-localhost}" \
            -p "${DB_PORT:-5432}" \
            -U "$db_user" \
            -d "$db_name" "$@"
    fi
}

test_data_tables() {
    local db_user="$1"
    local db_name="$2"
    local unsafe_tables
    if ! unsafe_tables=$(test_db_psql \
        "$db_user" \
        "$db_name" \
        -t -A -c "
SELECT count(*) FROM pg_tables
WHERE schemaname = 'public'
  AND tablename NOT IN ('role', 'capability', 'role_capability', 'flyway_schema_history')
  AND (tablename ~ E'[\\r\\n|]' OR tablename <> btrim(tablename));
"); then
        return 1
    fi
    unsafe_tables=${unsafe_tables//[[:space:]]/}
    if [ "$unsafe_tables" != 0 ]; then
        printf 'unsafe test table identifier discovered\n' >&2
        return 1
    fi

    local discovered_tables
    if ! discovered_tables=$(test_db_psql \
        "$db_user" \
        "$db_name" \
        -t -A -c "
SELECT tablename FROM pg_tables
WHERE schemaname = 'public'
  AND tablename NOT IN ('role', 'capability', 'role_capability', 'flyway_schema_history')
  AND EXISTS (
    SELECT 1 FROM information_schema.tables t2
    WHERE t2.table_schema = 'public'
      AND t2.table_name = pg_tables.tablename
      AND t2.table_type = 'BASE TABLE'
  )
ORDER BY tablename;
"); then
        return 1
    fi
    if [ -n "$discovered_tables" ]; then
        printf '%s\n' "$discovered_tables"
    fi
}

# --- quote_sql_identifier IDENTIFIER ---
quote_sql_identifier() {
    local identifier="$1"
    identifier=${identifier//\"/\"\"}
    printf '"%s"' "$identifier"
}

# --- port_is_listening PORT ---
# Returns 0 if PORT is in LISTEN state (checks lsof, then ss).
port_is_listening() {
    local port="$1"
    if command -v lsof &>/dev/null; then
        lsof -i ":$port" -P 2>/dev/null | grep -q LISTEN && return 0
    elif command -v ss &>/dev/null; then
        ss -tlnp "sport = :$port" 2>/dev/null | grep -q LISTEN && return 0
    fi
    return 1
}

# --- kill_cleanup PID ---
# Gracefully stops PID: SIGTERM, wait 10s, then SIGKILL + wait.
kill_cleanup() {
    local pid="$1"
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 10); do
        kill -0 "$pid" 2>/dev/null || return 0
        sleep 1
    done
    kill -9 "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
}

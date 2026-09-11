#!/bin/bash
# Database-test helper policy for project scripts (map #533 #569).
# Owns test-DB naming/discovery/psql transport plus SQL identifier quoting:
# test_db_name, test_db_psql, test_data_tables, quote_sql_identifier.
# Generic shell mechanics (log, ensure_root_dir, source_env, port_is_listening,
# kill_cleanup) come from tools/quality/lib/shell-common.sh, sourced below
# relative to this file so it resolves from any working directory.
#
# Source this file at the top of each database script:
#   source "$ROOT_DIR/tools/database/lib/db-common.sh"
# Caller must set ROOT_DIR before sourcing, or call ensure_root_dir afterwards.

# shellcheck source=../../quality/lib/shell-common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../quality/lib" && pwd)/shell-common.sh"

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
    if [ -n "${TEST_DB_CONTAINER:-}" ]; then
        docker exec "$TEST_DB_CONTAINER" psql -U "$db_user" -d "$db_name" "$@"
    elif command -v psql >/dev/null 2>&1; then
        PGPASSWORD="${POSTGRES_PASSWORD:-}" psql \
            -h "${DB_HOST:-localhost}" \
            -p "${DB_PORT:-5432}" \
            -U "$db_user" \
            -d "$db_name" "$@"
    else
        printf '%s\n' "test_db_psql: no transport: TEST_DB_CONTAINER is unset and psql is not in PATH" >&2
        return 1
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

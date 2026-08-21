#!/bin/bash
# Truncate all user-data tables in the test database, preserving seed tables
# (role, capability, role_capability) and Flyway metadata.
# Run after k6 load tests to restore the cleanliness invariant required by
# scripts/check-test-cleanliness.sh and the pre-commit/pre-push hooks.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
source "$ROOT_DIR/scripts/lib/common.sh"

source_env

DB_NAME="$(test_db_name)"
DB_USER="${POSTGRES_USER:-company_user}"

log clean-test-db "Truncating user-data tables in test DB '$DB_NAME'..."

TABLES_OUTPUT=$(test_data_tables "$DB_USER" "$DB_NAME")

if [ -z "$TABLES_OUTPUT" ]; then
    log clean-test-db "No user-data tables found. Nothing to truncate."
    exit 0
fi

QUOTED_TABLES=""
while IFS= read -r table; do
    [ -n "$table" ] || continue
    QUOTED_TABLES+="$(quote_sql_identifier "$table"),"
done <<< "$TABLES_OUTPUT"
QUOTED_TABLES=${QUOTED_TABLES%,}

log clean-test-db "Truncating tables: $QUOTED_TABLES"

test_db_psql \
    "$DB_USER" \
    "$DB_NAME" \
    -c "TRUNCATE TABLE $QUOTED_TABLES CASCADE;" \
    2>&1 | tail -5

log clean-test-db "Verifying cleanliness..."
if bash "$SCRIPT_DIR/check-test-cleanliness.sh" 2>&1 | tail -5; then
    log clean-test-db "Test DB is clean."
else
    log clean-test-db "ERROR: Test DB still has leftover data after truncation."
    exit 1
fi

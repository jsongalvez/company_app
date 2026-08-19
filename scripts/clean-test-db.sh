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

SEED_TABLES="role capability role_capability flyway_schema_history"

TABLES=$(docker exec company-postgres psql \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -t -A -c "
SELECT string_agg(tablename, ', ') FROM pg_tables
WHERE schemaname = 'public'
  AND tablename NOT IN ($(echo $SEED_TABLES | sed "s/ /', '/g" | sed "s/^/'/;s/$/'/"))
  AND EXISTS (
    SELECT 1 FROM information_schema.tables t2
    WHERE t2.table_schema = 'public' AND t2.table_name = pg_tables.tablename
    AND t2.table_type = 'BASE TABLE'
  );
")

if [ -z "$TABLES" ]; then
    log clean-test-db "No user-data tables found. Nothing to truncate."
    exit 0
fi

log clean-test-db "Truncating tables: $TABLES"

docker exec company-postgres psql \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -c "TRUNCATE TABLE $TABLES CASCADE;" \
    2>&1 | tail -5

log clean-test-db "Verifying cleanliness..."
if bash "$SCRIPT_DIR/check-test-cleanliness.sh" 2>&1 | tail -5; then
    log clean-test-db "Test DB is clean."
else
    log clean-test-db "ERROR: Test DB still has leftover data after truncation."
    exit 1
fi

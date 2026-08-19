#!/bin/bash
# Check that the test database has zero leftover rows in any test-managed table
# after a full test-suite run. Only seed tables (role, capability, role_capability)
# and Flyway metadata are expected to have rows.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
source "$ROOT_DIR/scripts/lib/common.sh"

source_env

DB_NAME="$(test_db_name)"
DB_USER="${POSTGRES_USER:-company_user}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

log cleanliness "Checking test DB '$DB_NAME' for leftover test data..."

# Seed tables that are expected to have rows
SEED_TABLES="role capability role_capability flyway_schema_history"

RESULT=$(docker exec company-postgres psql \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -t -A -c "
SELECT string_agg(tablename, ' ') FROM pg_tables
WHERE schemaname = 'public'
  AND tablename NOT IN ($(echo $SEED_TABLES | sed "s/ /', '/g" | sed "s/^/'/;s/$/'/"))
  AND EXISTS (
    SELECT 1 FROM information_schema.tables t2
    WHERE t2.table_schema = 'public' AND t2.table_name = pg_tables.tablename
    AND t2.table_type = 'BASE TABLE'
  );
")

TABLES_TO_CHECK=$(echo "$RESULT" | tr ' ' '\n' | sort | tr '\n' ' ' | xargs)

if [ -z "$TABLES_TO_CHECK" ]; then
    log cleanliness "No tables to check — test DB may not be initialized. Skipping."
    exit 0
fi

log cleanliness "Checking tables: $TABLES_TO_CHECK"

# Build one identifier-quoted query so cleanliness checks use one database round trip.
COUNT_QUERY=$(docker exec company-postgres psql \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -t -A -c "
SELECT string_agg(
    format('SELECT %L AS table_name, count(*) AS row_count FROM %I', tablename, tablename),
    ' UNION ALL '
)
FROM pg_tables
WHERE schemaname = 'public'
  AND tablename NOT IN ($(echo $SEED_TABLES | sed "s/ /', '/g" | sed "s/^/'/;s/$/'/"));
" 2>/dev/null)

if [ -z "$COUNT_QUERY" ]; then
    log cleanliness "ERROR: Could not build test-data count query."
    exit 1
fi

COUNTS=$(docker exec company-postgres psql \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -t -A -F '|' -c "$COUNT_QUERY")

LEAKED=""
while IFS='|' read -r tbl count; do
    if [ -n "$tbl" ] && [ "$count" != "0" ]; then
        LEAKED="$LEAKED $tbl($count)"
    fi
done <<< "$COUNTS"

if [ -n "$LEAKED" ]; then
    log cleanliness "ERROR: Test-data leak detected in tables:$LEAKED"
    log cleanliness "All test tables must have zero rows after the test suite completes."
    exit 1
fi

log cleanliness "All test tables are clean."

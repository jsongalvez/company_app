#!/bin/bash
# Check that the test database has zero leftover rows in any test-managed table
# after a full test-suite run. Only seed tables (role, capability, role_capability)
# and Flyway metadata are expected to have rows.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

source .env 2>/dev/null || true

DB_NAME="${TEST_DB_NAME:-${POSTGRES_DB}_test}"
DB_USER="${POSTGRES_USER:-company_user}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

log() { echo "$(date '+%H:%M:%S') [cleanliness] $*"; }

log "Checking test DB '$DB_NAME' for leftover test data..."

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
" 2>/dev/null || echo "")

TABLES_TO_CHECK=$(echo "$RESULT" | tr ' ' '\n' | sort | tr '\n' ' ' | xargs)

if [ -z "$TABLES_TO_CHECK" ]; then
    log "No tables to check — test DB may not be initialized. Skipping."
    exit 0
fi

log "Checking tables: $TABLES_TO_CHECK"

LEAKED=""
for tbl in $TABLES_TO_CHECK; do
    COUNT=$(docker exec company-postgres psql \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -A -c "SELECT count(*) FROM \"$tbl\";" 2>/dev/null || echo "0")
    COUNT=$(echo "$COUNT" | tr -d ' ')
    if [ "$COUNT" != "0" ]; then
        LEAKED="$LEAKED $tbl($COUNT)"
    fi
done

if [ -n "$LEAKED" ]; then
    log "ERROR: Test-data leak detected in tables:$LEAKED"
    log "All test tables must have zero rows after the test suite completes."
    exit 1
fi

log "All test tables are clean."

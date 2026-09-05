#!/bin/bash
# Check that the PUBLIC schema of the test database has zero leftover rows in any
# test-managed table. k6/manual public-DB cleanup evidence only (#493): backend test
# workers use owned test_w_* schemas and never touch public, so this script no longer
# evidences backend suites (see WorkerSchemaLifecycleTest + ADR-0006).
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

TABLES_OUTPUT=$(test_data_tables "$DB_USER" "$DB_NAME")

if [ -z "$TABLES_OUTPUT" ]; then
    log cleanliness "No tables to check — test DB may not be initialized. Skipping."
    exit 0
fi

mapfile -t TABLES_TO_CHECK <<< "$TABLES_OUTPUT"
TABLES_TO_LOG=$(printf '%s\n' "${TABLES_TO_CHECK[@]}" | paste -sd ' ' -)
log cleanliness "Checking tables: $TABLES_TO_LOG"

COUNT_QUERY=""
for table in "${TABLES_TO_CHECK[@]}"; do
    quoted_table=$(quote_sql_identifier "$table")
    quoted_name=${table//\'/\'\'}
    COUNT_QUERY+="SELECT '$quoted_name' AS table_name, count(*) AS row_count FROM $quoted_table UNION ALL "
done
COUNT_QUERY=${COUNT_QUERY% UNION ALL }

COUNTS=$(test_db_psql \
    "$DB_USER" \
    "$DB_NAME" \
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

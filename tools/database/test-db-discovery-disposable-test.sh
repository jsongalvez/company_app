#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$ROOT_DIR/tools/database/lib/db-common.sh"

source_env
DB_NAME="$(test_db_name)"
DB_USER="${POSTGRES_USER:-company_user}"
APP_DB="${POSTGRES_DB:-company_app}"
FIXTURE_TABLE="wayfinder_243_fixture"
UNSAFE_TABLE="wayfinder_243|unsafe"

if [ "$DB_NAME" = "$APP_DB" ]; then
    printf 'refusing to run disposable fixture against application database\n' >&2
    exit 1
fi

drop_fixture() {
    if ! docker exec company-postgres psql -U "$DB_USER" -d "$DB_NAME" \
        -c "DROP TABLE IF EXISTS \"$FIXTURE_TABLE\", \"$UNSAFE_TABLE\";" >/dev/null; then
        printf 'failed to drop disposable fixture tables\n' >&2
    fi
}
trap drop_fixture EXIT

docker exec company-postgres psql -U "$DB_USER" -d "$DB_NAME" \
    -c "CREATE TABLE \"$FIXTURE_TABLE\" (id integer NOT NULL); INSERT INTO \"$FIXTURE_TABLE\" VALUES (1);" \
    >/dev/null

if ! test_data_tables "$DB_USER" "$DB_NAME" | grep -Fxq "$FIXTURE_TABLE"; then
    printf 'discovery did not return fixture table\n' >&2
    exit 1
fi

bash "$SCRIPT_DIR/clean-test-db.sh" >/dev/null

remaining=$(docker exec company-postgres psql -U "$DB_USER" -d "$DB_NAME" \
    -t -A -c "SELECT count(*) FROM \"$FIXTURE_TABLE\";")
[ "$(printf '%s' "$remaining" | tr -d '[:space:]')" = 0 ]

docker exec company-postgres psql -U "$DB_USER" -d "$DB_NAME" \
    -c "CREATE TABLE \"$UNSAFE_TABLE\" (id integer NOT NULL); INSERT INTO \"$UNSAFE_TABLE\" VALUES (1);" \
    >/dev/null
if test_data_tables "$DB_USER" "$DB_NAME" >/dev/null 2>&1; then
    printf 'unsafe identifier was not rejected\n' >&2
    exit 1
fi

printf 'disposable test DB discovery passed\n'

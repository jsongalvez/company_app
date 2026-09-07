#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/tools/database/lib/db-common.sh"

run_case() {
    local name="$1"
    local expected="$2"
    local postgres_db="$3"
    local test_db="${4:-}"
    local actual
    if [ -n "$test_db" ]; then
        actual="$(env -i PATH="$PATH" POSTGRES_DB="$postgres_db" TEST_DB_NAME="$test_db" \
            bash -c "source '$ROOT_DIR/tools/database/lib/db-common.sh'; test_db_name")"
    else
        actual="$(env -i PATH="$PATH" POSTGRES_DB="$postgres_db" \
            bash -c "source '$ROOT_DIR/tools/database/lib/db-common.sh'; test_db_name")"
    fi
    if [ "$actual" != "$expected" ]; then
        printf 'case %s: expected %s, got %s\n' "$name" "$expected" "$actual" >&2
        exit 1
    fi
}

run_case derived company_app_test company_app
run_case explicit custom_test_db company_app custom_test_db

selected="$(env -i PATH="$PATH" POSTGRES_DB=company_app bash -c \
    "source '$ROOT_DIR/tools/database/lib/db-common.sh'; K6_DB_NAME=\"\${TEST_DB_NAME:-company_app_test}\"; export TEST_DB_NAME=\"\$K6_DB_NAME\"; test_db_name")"
if [ "$selected" != "company_app_test" ]; then
    printf 'case k6 default: expected company_app_test, got %s\n' "$selected" >&2
    exit 1
fi

printf 'test DB name fixtures: PASS\n'

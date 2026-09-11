#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/tools/database/lib/db-common.sh"

# Force the fixture through its mocked Docker transport even when host psql exists.
export TEST_DB_CONTAINER="${TEST_DB_CONTAINER:-company-postgres}"

docker() {
    if [ "${FAKE_DOCKER_FAIL:-0}" = 1 ]; then
        return 1
    fi
    if [ "${FAKE_DOCKER_EMPTY:-0}" = 1 ]; then
        return 0
    fi
    case "$*" in
        *"SELECT count(*)"*) printf '0\n'; return 0 ;;
    esac
    printf 'accounts\norder details\nusers\n'
}

discovered=$(test_data_tables test_user company_app_test)
[ "$discovered" = $'accounts\norder details\nusers' ]
[ "$(quote_sql_identifier 'order details')" = '"order details"' ]
[ -z "$(FAKE_DOCKER_EMPTY=1 test_data_tables test_user company_app_test)" ]

if FAKE_DOCKER_FAIL=1 test_data_tables test_user company_app_test >/dev/null 2>&1; then
    printf 'discovery failure was not propagated\n' >&2
    exit 1
fi

# No-transport refusal (ref #887): no container and no psql on PATH fails
# closed naming both, instead of an unbound-variable abort. The inner shell
# runs on a stub PATH (dirname only) so psql stays unresolvable; sourcing
# needs nothing else at source time.
stub_dir="$(mktemp -d)"
ln -s "$(command -v dirname)" "$stub_dir/dirname"
bash_bin="$(command -v bash)"
if env -u TEST_DB_CONTAINER PATH="$stub_dir" "$bash_bin" -c "source \"$ROOT_DIR/tools/database/lib/db-common.sh\"; test_db_psql test_user company_app_test -t -A -c 'SELECT 1'" >/dev/null 2>&1; then
    printf 'no-transport invocation unexpectedly succeeded\n' >&2
    rm -rf "$stub_dir"
    exit 1
fi
refusal_output="$(env -u TEST_DB_CONTAINER PATH="$stub_dir" "$bash_bin" -c "source \"$ROOT_DIR/tools/database/lib/db-common.sh\"; test_db_psql test_user company_app_test -t -A -c 'SELECT 1'" 2>&1 || true)"
rm -rf "$stub_dir"
case "$refusal_output" in
    *TEST_DB_CONTAINER*psql*|*psql*TEST_DB_CONTAINER*) ;;
    *)
        printf 'no-transport refusal names neither transport: %s\n' "$refusal_output" >&2
        exit 1
        ;;
esac

printf 'test DB discovery tests passed\n'

#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/lib/common.sh"

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

printf 'test DB discovery tests passed\n'

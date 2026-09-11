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

# --- clean-test-db.sh application-DB refusal guard (ref #885) ---
# Resolved test DB == application DB must exit 1 before any discovery/TRUNCATE.
# clean-test-db.sh calls source_env internally, so a local .env (if any) is
# hidden for these cases: file assignments would otherwise clobber the case env
# (that precedence is #886's scope, not this guard's). Restored on EXIT.
GUARD_ENV_HIDDEN=0
if [ -e "$ROOT_DIR/.env" ]; then
    mv "$ROOT_DIR/.env" "$ROOT_DIR/.env.wayfinder-885-bak"
    GUARD_ENV_HIDDEN=1
fi
GUARD_STUB_DIR="$(mktemp -d)"
printf '#!/bin/bash\nprintf "stub-transport-invoked: %%s\\n" "$*" >&2\nexit 99\n' > "$GUARD_STUB_DIR/psql"
printf '#!/bin/bash\nprintf "stub-transport-invoked: %%s\\n" "$*" >&2\nexit 99\n' > "$GUARD_STUB_DIR/docker"
chmod +x "$GUARD_STUB_DIR/psql" "$GUARD_STUB_DIR/docker"
guard_cleanup() {
    rm -rf "$GUARD_STUB_DIR"
    if [ "$GUARD_ENV_HIDDEN" = 1 ]; then
        mv "$ROOT_DIR/.env.wayfinder-885-bak" "$ROOT_DIR/.env"
    fi
}
trap guard_cleanup EXIT

run_guard_case() {
    local name="$1"
    local postgres_db="$2"
    local test_db="${3:-}"
    local guard_out
    local guard_rc
    if [ -n "$test_db" ]; then
        if guard_out="$(env -i PATH="$GUARD_STUB_DIR:$PATH" POSTGRES_DB="$postgres_db" TEST_DB_NAME="$test_db" \
                bash "$ROOT_DIR/tools/database/clean-test-db.sh" 2>&1)"; then
            guard_rc=0
        else
            guard_rc=$?
        fi
    else
        if guard_out="$(env -i PATH="$GUARD_STUB_DIR:$PATH" POSTGRES_DB="$postgres_db" \
                bash "$ROOT_DIR/tools/database/clean-test-db.sh" 2>&1)"; then
            guard_rc=0
        else
            guard_rc=$?
        fi
    fi
    case "$name" in
        refuse)
            [ "$guard_rc" = 1 ] || { printf 'guard %s: expected exit 1, got %s\n%s\n' "$name" "$guard_rc" "$guard_out" >&2; exit 1; }
            case "$guard_out" in
                *"refusing to run clean-test-db against application database"*) ;;
                *) printf 'guard %s: refusal message missing\n%s\n' "$name" "$guard_out" >&2; exit 1 ;;
            esac
            case "$guard_out" in
                *"test DB 'company_app' == application DB 'company_app'"*) ;;
                *) printf 'guard %s: message must name both values\n%s\n' "$name" "$guard_out" >&2; exit 1 ;;
            esac
            case "$guard_out" in
                *"stub-transport-invoked"*) printf 'guard %s: reached DB transport before refusing\n%s\n' "$name" "$guard_out" >&2; exit 1 ;;
            esac
            ;;
        proceed)
            case "$guard_out" in
                *"refusing to run clean-test-db against application database"*)
                    printf 'guard %s: distinct DBs must not refuse\n%s\n' "$name" "$guard_out" >&2; exit 1 ;;
            esac
            case "$guard_out" in
                *"stub-transport-invoked"*) ;;
                *) printf 'guard %s: distinct DBs must proceed to discovery (stub transport unreached)\n%s\n' "$name" "$guard_out" >&2; exit 1 ;;
            esac
            ;;
    esac
}

run_guard_case refuse company_app company_app
run_guard_case proceed company_app company_app_test
run_guard_case proceed company_app

printf 'test DB name fixtures: PASS\n'

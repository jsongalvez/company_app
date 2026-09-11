#!/bin/bash
# Fixtures for source_env export preservation (issue #886): pre-exported
# caller values survive sourcing .env; .env still fills genuinely-unset vars;
# the k6 override scenario converges (boot DB == cleanup DB). Plain bash —
# no Gradle, database, or network.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$ROOT_DIR/tools/database/lib/db-common.sh"

FAKE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FAKE_ROOT"' EXIT
cat > "$FAKE_ROOT/.env" <<'EOF'
SOURCE_ENV_FIXTURE_FILL=from_dotenv_file
SOURCE_ENV_FIXTURE_CLASH=from_dotenv_file
TEST_DB_NAME=from_dotenv_file
DB_HOST=from_dotenv_file
POSTGRES_DB=from_dotenv_file
# a comment line must not break key extraction
export SOURCE_ENV_FIXTURE_EXPORTED=from_dotenv_file
EOF

# Case 1: pre-exported caller values survive (the k6 operator-override path).
(
    export SOURCE_ENV_FIXTURE_CLASH=custom SOURCE_ENV_FIXTURE_EXPORTED=custom
    export TEST_DB_NAME=operator_db DB_HOST=operator_host POSTGRES_DB=operator_appdb
    ROOT_DIR="$FAKE_ROOT" source_env
    [ "$SOURCE_ENV_FIXTURE_CLASH" = custom ]
    [ "$SOURCE_ENV_FIXTURE_EXPORTED" = custom ]
    [ "$TEST_DB_NAME" = operator_db ]
    [ "$DB_HOST" = operator_host ]
    [ "$POSTGRES_DB" = operator_appdb ]
    case "$(declare -p TEST_DB_NAME)" in declare\ -*x*) ;; *)
        printf 'export attribute lost for TEST_DB_NAME\n' >&2
        exit 1
        ;;
    esac
    # Cleanup-path derivation converges with the boot path on the operator DB.
    [ "$(test_db_name)" = operator_db ]
)

# Case 2: genuinely-unset vars are still filled from .env (documented default).
(
    unset SOURCE_ENV_FIXTURE_FILL SOURCE_ENV_FIXTURE_CLASH SOURCE_ENV_FIXTURE_EXPORTED
    unset TEST_DB_NAME DB_HOST POSTGRES_DB
    ROOT_DIR="$FAKE_ROOT" source_env
    [ "$SOURCE_ENV_FIXTURE_FILL" = from_dotenv_file ]
    [ "$SOURCE_ENV_FIXTURE_CLASH" = from_dotenv_file ]
    [ "$SOURCE_ENV_FIXTURE_EXPORTED" = from_dotenv_file ]
    [ "$TEST_DB_NAME" = from_dotenv_file ]
    [ "$(test_db_name)" = from_dotenv_file ]
)

# Case 3: non-exported caller-set vars survive too.
(
    unset SOURCE_ENV_FIXTURE_CLASH
    SOURCE_ENV_FIXTURE_CLASH=plain_custom
    ROOT_DIR="$FAKE_ROOT" source_env
    [ "$SOURCE_ENV_FIXTURE_CLASH" = plain_custom ]
)

# Case 4: missing .env stays a silent no-op.
(
    mkdir -p "$FAKE_ROOT/empty"
    ROOT_DIR="$FAKE_ROOT/empty" source_env
)

printf 'source_env export-preservation fixtures: PASS\n'

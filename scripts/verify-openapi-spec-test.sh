#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

FIXTURE="$WORK_DIR/repo"
MAPPING_DIR="$FIXTURE/backend/src/main/kotlin/com/companyb/companyapp/api/mapping"
mkdir -p \
    "$FIXTURE/scripts" \
    "$FIXTURE/backend/src/main/kotlin/com/companyb/companyapp/api/routes" \
    "$FIXTURE/backend/src/main/kotlin/com/companyb/companyapp/service" \
    "$FIXTURE/shared/src/commonMain/kotlin/com/companyb/companyapp/dto" \
    "$FIXTURE/shared/src/commonMain/kotlin/com/companyb/companyapp/domain" \
    "$WORK_DIR/bin"
cp "$ROOT_DIR/scripts/verify-openapi-spec.sh" "$FIXTURE/scripts/verify-openapi-spec.sh"
chmod +x "$FIXTURE/scripts/verify-openapi-spec.sh"
printf '{}\n' > "$WORK_DIR/spec.json"

printf '%s\n' \
    '#!/bin/bash' \
    'printf "%s\\n" "$@" > "$FIND_LOG"' \
    > "$WORK_DIR/bin/find"
chmod +x "$WORK_DIR/bin/find"
printf '%s\n' '#!/bin/bash' 'exit 0' > "$WORK_DIR/bin/node"
chmod +x "$WORK_DIR/bin/node"

run_fixture() {
    local name="$1"
    local find_log="$WORK_DIR/$name-find.log"
    FIND_LOG="$find_log" PATH="$WORK_DIR/bin:$PATH" \
        bash "$FIXTURE/scripts/verify-openapi-spec.sh" "$WORK_DIR/spec.json" >/dev/null
    printf '%s\n' "$find_log"
}

absent_log="$(run_fixture absent)"
if grep -Fq "$MAPPING_DIR" "$absent_log"; then
    printf 'optional mapping directory was passed to find while absent\n' >&2
    exit 1
fi

mkdir -p "$MAPPING_DIR"
present_log="$(run_fixture present)"
grep -Fq "$MAPPING_DIR" "$present_log"

printf 'OpenAPI freshness optional-directory fixtures: PASS\n'

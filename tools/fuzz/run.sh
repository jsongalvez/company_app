#!/usr/bin/env bash
# Fuzz every route in the OpenAPI contract as each seeded role (and anonymously).
# Needs a running backend with the dev seed logins (see tools/fuzz/README.md) and psql.
#   FUZZ_URL=http://localhost:18080 tools/fuzz/run.sh [max-examples]
# Pass = no 5xx and every response matches its schema. Exit 1 otherwise.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
URL="${FUZZ_URL:-http://localhost:18080}"
EXAMPLES="${1:-${FUZZ_EXAMPLES:-10}}"
SEED="${FUZZ_SEED:-1}"
SPEC="$ROOT/backend/build/openapi/openapi-canonical.json"
OUT="${FUZZ_OUT:-$ROOT/build/fuzz}"
SCHEMATHESIS="schemathesis==4.32.3"
mkdir -p "$OUT"
# CI installs the pinned version with pip; locally uvx fetches it on demand.
if command -v schemathesis >/dev/null 2>&1; then st() { schemathesis "$@"; }; else st() { uvx --from "$SCHEMATHESIS" schemathesis "$@"; }; fi

[ -f "$SPEC" ] || (cd "$ROOT" && ./gradlew :backend:exportOpenApiSpec -q) || exit 2
python3 -I "$HERE/seed.py" "$URL" "$OUT/params.toml" || exit 2

login() { # role-name username password -> token
  curl -sf -X POST "$URL/auth/login" -H 'content-type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python3 -I -c 'import json,sys;print(json.load(sys.stdin)["token"])'
}

declare -A TOKEN
TOKEN[owner]="$(login "${TEST_USERNAME:-owner}" "${TEST_PASSWORD:-owner-dev-pass}")"
TOKEN[staff]="$(login "${SCOPED_USERNAME:-staff}" "${SCOPED_PASSWORD:-staff-dev-pass}")"
TOKEN[relief]="$(login "${RELIEF_USERNAME:-relief}" "${RELIEF_PASSWORD:-relief-dev-pass}")"
TOKEN[superuser]="$(login dev dev-dev-pass)"
TOKEN[anonymous]=""
for r in owner staff relief superuser; do [ -n "${TOKEN[$r]}" ] || { echo "login failed for $r (is the backend seeded with the dev logins?)"; exit 2; }; done

pids=(); roles=()
for role in owner staff relief superuser anonymous; do
  hdr=(); [ -n "${TOKEN[$role]}" ] && hdr=(-H "Authorization: Bearer ${TOKEN[$role]}")
  # Anonymous only ever sees 401s: a few cases per route are enough to prove the guard never 5xxs.
  # The coverage phase (boundary and negative cases, ~4k requests) runs once, as the role that reaches the most routes.
  phases="fuzzing"; n="$EXAMPLES"
  [ "$role" = owner ] && phases="coverage,fuzzing"
  [ "$role" = anonymous ] && n=2
  st --config-file "$OUT/params.toml" run "$SPEC" --url "$URL" "${hdr[@]}" \
    --checks not_a_server_error,response_schema_conformance \
    --phases "$phases" --max-examples "$n" --seed "$SEED" --workers 2 \
    --exclude-path-regex '^/(api/auth/|auth/|api/me/password$)' --no-color >"$OUT/$role.log" 2>&1 &
  pids+=($!); roles+=("$role")
done
rc=0
for i in "${!pids[@]}"; do
  wait "${pids[$i]}" || { echo "FAIL ${roles[$i]} (see $OUT/${roles[$i]}.log)"; rc=1; }
  sed -n '/SUMMARY/,$p' "$OUT/${roles[$i]}.log" | grep -E 'Test cases|generated|failed|FAIL' | sed "s/^/${roles[$i]}: /"
done
exit $rc

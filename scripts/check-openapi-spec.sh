#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

# Compilation owns generation; its finalized task normalizes the artifact before
# this verifier reads it. Keeping both steps here makes this the one build gate.
./gradlew :backend:compileKotlin --no-daemon

spec="$repo_root/backend/build/tmp/kapt3/classes/main/openapi-plugin/openapi-default.json"
verification_output=$(./scripts/verify-openapi-spec.sh "$spec")
printf '%s\n' "$verification_output"
grep -Fxq "OPENAPI_ROUTE_COVERAGE_OK" <<<"$verification_output"
grep -Fxq "OPENAPI_SECRET_SCAN_OK" <<<"$verification_output"

# Negative control: a route drift must fail closed, without changing checkout.
drifted_spec=$(mktemp)
trap 'rm -f "$drifted_spec"' EXIT
cp "$spec" "$drifted_spec"
node --input-type=module - "$drifted_spec" <<'NODE'
import fs from "node:fs";
const file = process.argv[2];
const spec = JSON.parse(fs.readFileSync(file, "utf8"));
const path = Object.keys(spec.paths ?? {})[0];
if (!path) throw new Error("OpenAPI document has no paths");
delete spec.paths[path];
fs.writeFileSync(file, JSON.stringify(spec));
NODE

if drift_output=$(OPENAPI_VERIFY_SKIP_FRESHNESS=1 ./scripts/verify-openapi-spec.sh "$drifted_spec" 2>&1); then
  printf '%s\n' "$drift_output" >&2
  echo "OpenAPI negative drift control unexpectedly passed" >&2
  exit 1
fi
if grep -Eq 'OPENAPI_(ROUTE_COVERAGE|SECRET_SCAN)_OK' <<<"$drift_output"; then
  printf '%s\n' "$drift_output" >&2
  echo "OpenAPI verifier emitted success marker after failure" >&2
  exit 1
fi
echo "OPENAPI_NEGATIVE_DRIFT_OK"

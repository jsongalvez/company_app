#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"
unset OPENAPI_ROUTE_CONTRACT_PATH OPENAPI_TEST_MODE UPDATE_OPENAPI_ROUTE_CONTRACT

# Compilation (kapt) generates the spec; publishOpenApiSpec then normalizes it
# before this verifier reads it. Normalization is invoked explicitly here — never
# via a compile finalizer — so JVM-only Docker builders can :backend:installDist
# without Node.js (#372).
./gradlew :backend:compileKotlin :backend:publishOpenApiSpec

spec="$repo_root/backend/build/tmp/kapt3/classes/main/openapi-plugin/openapi-default.json"
verification_output=$(./scripts/verify-openapi-spec.sh "$spec")
printf '%s\n' "$verification_output"
grep -Fxq "OPENAPI_ROUTE_COVERAGE_OK" <<<"$verification_output"
grep -Fxq "OPENAPI_SECRET_SCAN_OK" <<<"$verification_output"

# Negative control: a route drift must fail closed, without changing checkout.
temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
drifted_spec="$temp_dir/drifted.json"
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

# Negative control: a stale fingerprint must fail before changing checkout.
stale_contract="$temp_dir/stale-contract.json"
stale_output="$temp_dir/stale-output.json"
stale_log="$temp_dir/stale.log"
cp "$repo_root/scripts/openapi-route-contract.json" "$stale_contract"
node --input-type=module - "$stale_contract" <<'NODE'
import fs from "node:fs";
const file = process.argv[2];
const contract = JSON.parse(fs.readFileSync(file, "utf8"));
contract.fingerprint = "stale";
fs.writeFileSync(file, JSON.stringify(contract));
NODE
if OPENAPI_TEST_MODE=1 env -u UPDATE_OPENAPI_ROUTE_CONTRACT OPENAPI_ROUTE_CONTRACT_PATH="$stale_contract" node "$repo_root/scripts/normalize-openapi-spec.mjs" "$spec" "$stale_output" >"$stale_log" 2>&1; then
  echo "OpenAPI stale fingerprint control unexpectedly passed" >&2
  exit 1
fi
grep -Fq "OpenAPI route contract fingerprint is stale" "$stale_log"
echo "OPENAPI_STALE_FINGERPRINT_OK"

# Negative control: a DTO-schema drift must move the fingerprint (#459). Copy the DTO
# sources, add one probe field, and assert the stored fingerprint rejects it.
schema_dto_dir="$temp_dir/dto"
schema_output="$temp_dir/schema-output.json"
schema_log="$temp_dir/schema.log"
cp -r "$repo_root/shared/src/commonMain/kotlin/com/companyb/companyapp/dto" "$schema_dto_dir"
cat >"$schema_dto_dir/FingerprintProbe.kt" <<'KT'
package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class FingerprintProbe(
    val probe: String,
)
KT
if OPENAPI_TEST_MODE=1 env -u UPDATE_OPENAPI_ROUTE_CONTRACT OPENAPI_DTO_DIR="$schema_dto_dir" node "$repo_root/scripts/normalize-openapi-spec.mjs" "$spec" "$schema_output" >"$schema_log" 2>&1; then
  echo "OpenAPI schema-drift control unexpectedly passed" >&2
  exit 1
fi
grep -Fq "OpenAPI route contract fingerprint is stale" "$schema_log"
echo "OPENAPI_SCHEMA_DRIFT_OK"

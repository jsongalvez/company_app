# Gates - 261: OpenAPI fingerprint

- [x] G1: Generated OpenAPI artifact matches committed route fingerprint
  CHECK: ./scripts/verify-openapi-spec.sh
  EXPECT: EXIT 0
  EVIDENCE: OPENAPI_ROUTE_COVERAGE_OK

- [x] G2: Stale route fingerprint fails closed
  CHECK: node --input-type=module -e 'import fs from "node:fs"; const file="/tmp/openapi-stale-contract.json"; const contract=JSON.parse(fs.readFileSync("scripts/openapi-route-contract.json")); contract.fingerprint="stale"; fs.writeFileSync(file, JSON.stringify(contract));' && ! OPENAPI_TEST_MODE=1 OPENAPI_ROUTE_CONTRACT_PATH=/tmp/openapi-stale-contract.json node scripts/normalize-openapi-spec.mjs backend/build/tmp/kapt3/classes/main/openapi-plugin/openapi-default.json /tmp/openapi-stale-output.json 2>/tmp/openapi-stale-error.log && grep -Fq "OpenAPI route contract fingerprint is stale" /tmp/openapi-stale-error.log
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Full OpenAPI gate passes matching and route-drift controls
  CHECK: bash scripts/check-openapi-spec.sh
  EXPECT: MATCHES OPENAPI_NEGATIVE_DRIFT_OK
  EVIDENCE: To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.14.3/userguide/gradle_daemon.html#sec:disabling_the_da

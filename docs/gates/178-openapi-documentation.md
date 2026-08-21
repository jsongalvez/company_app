# Gates — Build: Add generated OpenAPI documentation to backend

- [x] G1: OpenAPI dependencies and one annotation-processing path are configured
  CHECK: grep -q 'javalin.openapi.plugin' backend/build.gradle.kts && grep -q 'kapt(libs.javalin.openapi.processor)' backend/build.gradle.kts && ! grep -q 'annotationProcessor(libs.javalin.openapi.processor)' backend/build.gradle.kts
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Backend compiles with OpenAPI annotation processing
  CHECK: ./gradlew :backend:compileKotlin
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G3: Generated OpenAPI document exists and declares OpenAPI 3.1
  CHECK: test -f backend/build/tmp/kapt3/classes/main/openapi-plugin/openapi-default.json && grep -q '"openapi"[[:space:]]*:[[:space:]]*"3.1.0"' backend/build/tmp/kapt3/classes/main/openapi-plugin/openapi-default.json
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G4: One ordered gate generates, normalizes, verifies, and negative-tests the contract
  CHECK: bash scripts/check-openapi-spec.sh
  EXPECT: MATCHES OPENAPI_NEGATIVE_DRIFT_OK
  EVIDENCE: To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.14.3/userguide/gradle_daemon.html#sec:disabling_the_da

- [x] G5: Generated documentation contains metadata, bearer scheme, path parameters, and no configured secret values
  CHECK: ./scripts/verify-openapi-spec.sh | grep OPENAPI_SECRET_SCAN_OK
  EXPECT: MATCHES OPENAPI_SECRET_SCAN_OK
  EVIDENCE: OPENAPI_SECRET_SCAN_OK

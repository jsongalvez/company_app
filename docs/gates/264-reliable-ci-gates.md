# Gates — #264: restore reliable CI gates

- [x] G1: AppConfig accepts CI environment variables without a local `.env`
  CHECK: grep -Fq 'ignoreIfMissing = true' backend/src/main/kotlin/com/companyb/companyapp/config/AppConfig.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: CI supplies application port required by AppConfig
  CHECK: grep -Fq 'APP_PORT: 8080' .github/workflows/quality.yml
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: JMH workflow distinguishes benchmark task failure from comparison failure
  CHECK: grep -Fq 'Upstream benchmark task failed' .github/workflows/jmh.yml
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G4: OpenAPI contract check passes, including stale-fingerprint negative control
  CHECK: bash scripts/check-openapi-spec.sh
  EXPECT: MATCHES OPENAPI_STALE_FINGERPRINT_OK
  EVIDENCE: To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.14.3/userguide/gradle_daemon.html#sec:disabling_the_da

- [x] G5: JMH baseline fixtures preserve fail-closed parsing and regression behavior
  CHECK: bash scripts/check-baselines-test.sh
  EXPECT: MATCHES check-baselines fixtures: PASS
  EVIDENCE: check-baselines fixtures: PASS

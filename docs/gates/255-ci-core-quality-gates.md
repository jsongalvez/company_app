# Gates - #255 CI core quality gates

- [x] G1: CI workflow owns backend quality and shared JVM compilation
  CHECK: test -f .github/workflows/quality.yml && grep -q ':backend:detekt :backend:ktlintCheck :backend:test :shared:compileKotlinJvm' .github/workflows/quality.yml
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: CI workflow owns Compose Android and Desktop compilation
  CHECK: test -f .github/workflows/quality.yml && grep -q 'composeApp:compileDebugKotlinAndroid' .github/workflows/quality.yml && grep -q 'composeApp:compileKotlinDesktop' .github/workflows/quality.yml
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Specialized OpenAPI and JMH workflows remain independently owned
  CHECK: test -f .github/workflows/openapi.yml && test -f .github/workflows/jmh.yml && grep -q 'check-openapi-spec.sh' .github/workflows/openapi.yml && grep -q ':backend:jmh' .github/workflows/jmh.yml
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G4: CI workflow has least-privilege read permissions and cancels superseded runs
  CHECK: test -f .github/workflows/quality.yml && grep -q 'contents: read' .github/workflows/quality.yml && grep -q 'cancel-in-progress: true' .github/workflows/quality.yml
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G5: Test-database cleanliness supports host-based CI service databases
  CHECK: grep -q 'DB_HOST' scripts/lib/common.sh && grep -q 'TEST_DB_CONTAINER' scripts/lib/common.sh
  EXPECT: EXIT 0
  EVIDENCE: exit 0

# Gates — 268: fail pre-commit on compiler warnings

- [x] G1: Backend compiler tasks honor the pre-commit warnings-as-errors property
  CHECK: grep -n 'allWarningsAsErrors' build.gradle.kts
  EXPECT: MATCHES allWarningsAsErrors
  EVIDENCE: 40:        compilerOptions.allWarningsAsErrors.set(warningsAsErrors)

- [x] G2: Relief invite authorization tests contain no response-body double reads
  CHECK: ! grep -n -E 'assert[^\n]*body\?\.string\(\)|body!!\.string\(\).*body!!\.string\(\)' backend/src/test/kotlin/com/companyb/companyapp/api/routes/ReliefInviteAuthzTest.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Pre-commit documents compiler-warning enforcement
  CHECK: grep -n 'compiler warnings' .githooks/pre-commit backend/AGENTS.md
  EXPECT: MATCHES compiler warnings
  EVIDENCE: .githooks/pre-commit:61:log pre-commit "Running quality gate (detekt, ktlint, test, shared compile, compiler warnings)..."

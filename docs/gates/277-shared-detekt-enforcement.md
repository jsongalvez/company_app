# Gates — 277: shared Detekt enforcement

- [x] G1: Local pre-commit invokes typed shared Detekt tasks
   CHECK: grep -Fq ':shared:detektMetadataCommonMain' .githooks/pre-commit && grep -Fq ':shared:detektJvmMain' .githooks/pre-commit && grep -Fq ':shared:detektJvmTest' .githooks/pre-commit && grep -Fq ':shared:detektAndroidDebug' .githooks/pre-commit && grep -Fq ':shared:detektAndroidDebugUnitTest' .githooks/pre-commit && grep -Fq ':shared:detektIosArm64Main' .githooks/pre-commit && grep -Fq ':shared:detektIosArm64Test' .githooks/pre-commit && grep -Fq ':shared:detektIosSimulatorArm64Main' .githooks/pre-commit && grep -Fq ':shared:detektIosSimulatorArm64Test' .githooks/pre-commit && grep -Fq ':shared:jvmTest' .githooks/pre-commit
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: CI quality gate invokes the same typed shared Detekt task set
   CHECK: grep -Fq ':shared:detektMetadataCommonMain' .github/workflows/quality.yml && grep -Fq ':shared:detektJvmMain' .github/workflows/quality.yml && grep -Fq ':shared:detektJvmTest' .github/workflows/quality.yml && grep -Fq ':shared:detektAndroidDebug' .github/workflows/quality.yml && grep -Fq ':shared:detektAndroidDebugUnitTest' .github/workflows/quality.yml && grep -Fq ':shared:detektIosArm64Main' .github/workflows/quality.yml && grep -Fq ':shared:detektIosArm64Test' .github/workflows/quality.yml && grep -Fq ':shared:detektIosSimulatorArm64Main' .github/workflows/quality.yml && grep -Fq ':shared:detektIosSimulatorArm64Test' .github/workflows/quality.yml && grep -Fq ':shared:jvmTest' .github/workflows/quality.yml
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Shared common production sources and supported platform tasks pass Detekt
   CHECK: ./gradlew :shared:detektMetadataCommonMain :shared:detektJvmMain :shared:detektJvmTest :shared:detektAndroidDebug :shared:detektAndroidDebugUnitTest :shared:detektIosArm64Main :shared:detektIosArm64Test :shared:detektIosSimulatorArm64Main :shared:detektIosSimulatorArm64Test :shared:compileKotlinJvm :shared:jvmTest --no-daemon --offline --quiet
  EXPECT: EXIT 0
   EVIDENCE: exit 0

- [x] G4: Shared JVM compilation and tests remain green
   CHECK: ./gradlew :shared:compileKotlinJvm :shared:jvmTest --no-daemon --offline --quiet
  EXPECT: EXIT 0
   EVIDENCE: exit 0

Shared `commonTest` has no registered Detekt task in this Gradle/KMP setup; its
tests are covered by `:shared:jvmTest`. Platform and target-specific Detekt
tasks that report `NO-SOURCE` are retained in the command to make unsupported
coverage explicit rather than treating aggregate `:shared:detekt` as evidence.

# Gates: #278 Shared and Compose Test Detekt

## Coverage

- Shared `commonTest` has no Detekt task; `:shared:jvmTest` compiles and executes its two common tests. Android and iOS shared test Detekt tasks exist but are currently `NO-SOURCE`.
- Compose `commonTest` has no Detekt task; `:composeApp:desktopTest` compiles and executes its common tests. Android and iOS Compose test Detekt tasks exist but are currently `NO-SOURCE`.
- No Android instrumented or platform-specific iOS test source files exist, so their Detekt tasks are not applicable to this ticket.

- [x] G1: Shared and Compose platform test Detekt tasks pass
  CHECK: ./gradlew :shared:detektJvmTest :shared:detektAndroidDebugUnitTest :shared:detektIosArm64Test :shared:detektIosSimulatorArm64Test :composeApp:detektDesktopTest :composeApp:detektAndroidDebugUnitTest :composeApp:detektIosArm64Test :composeApp:detektIosSimulatorArm64Test --console=plain --no-daemon
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.14.3/userguide/gradle_daemon.html#sec:disabling_the_da

- [x] G2: Mandatory local hook includes every existing test Detekt task
  CHECK: grep -F ':shared:detektJvmTest' .githooks/pre-commit && grep -F ':shared:detektAndroidDebugUnitTest' .githooks/pre-commit && grep -F ':shared:detektIosArm64Test' .githooks/pre-commit && grep -F ':shared:detektIosSimulatorArm64Test' .githooks/pre-commit && grep -F ':composeApp:detektDesktopTest' .githooks/pre-commit && grep -F ':composeApp:detektAndroidDebugUnitTest' .githooks/pre-commit && grep -F ':composeApp:detektIosArm64Test' .githooks/pre-commit && grep -F ':composeApp:detektIosSimulatorArm64Test' .githooks/pre-commit && grep -F ':composeApp:desktopTest' .githooks/pre-commit
  EXPECT: EXIT 0
  EVIDENCE: :shared:detektMetadataCommonMain :shared:detektJvmMain :shared:detektJvmTest \

- [x] G3: CI includes every existing test Detekt task
  CHECK: grep -F ':shared:detektJvmTest' .github/workflows/quality.yml && grep -F ':shared:detektAndroidDebugUnitTest' .github/workflows/quality.yml && grep -F ':shared:detektIosArm64Test' .github/workflows/quality.yml && grep -F ':shared:detektIosSimulatorArm64Test' .github/workflows/quality.yml && grep -F ':composeApp:detektDesktopTest' .github/workflows/quality.yml && grep -F ':composeApp:detektAndroidDebugUnitTest' .github/workflows/quality.yml && grep -F ':composeApp:detektIosArm64Test' .github/workflows/quality.yml && grep -F ':composeApp:detektIosSimulatorArm64Test' .github/workflows/quality.yml && grep -F ':composeApp:desktopTest' .github/workflows/quality.yml
  EXPECT: EXIT 0
  EVIDENCE: :shared:detektJvmMain :shared:detektJvmTest

- [x] G4: Shared and Compose common tests pass
  CHECK: ./gradlew :shared:jvmTest :composeApp:desktopTest --console=plain --no-daemon
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.14.3/userguide/gradle_daemon.html#sec:disabling_the_da

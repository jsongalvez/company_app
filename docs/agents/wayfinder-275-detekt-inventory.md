# Detekt Inventory - Child #275

## Environment

- Detekt: `1.23.8`, from `gradle/libs.versions.toml:15`.
- Kotlin: `2.3.10`, from `gradle/libs.versions.toml:28`.
- Ktlint plugin: `14.1.0`; configured ktlint CLI version is `1.8.0`.
- Root `build.gradle.kts:17-40` applies Detekt and ktlint to every subproject, enables `buildUponDefaultConfig`, keeps `allRules=false`, and merges `detekt.yml` with `detekt-anti-slop.yml`.
- `detekt-formatting` is added to every subproject.
- `detekt.yml` and `detekt-anti-slop.yml` are byte-equivalent to `/home/ubuntu/anti-slop-detekt` (no diff).

## Source Sets And Tasks

Directory inventory:

- Backend: `backend/src/main`, `backend/src/test`.
- Shared: `commonMain`, `commonTest`; configured JVM, Android, iOS arm64, and iOS simulator arm64 targets.
- Compose: `commonMain`, `commonTest`, `androidMain`, `desktopMain`, `desktopTest`, `iosMain`.

Observed Gradle tasks from `./gradlew tasks --all --no-daemon`:

- Backend: `:backend:detekt`, `:backend:detektMain`, `:backend:detektTest`, `:backend:detektJmh`.
- Shared: aggregate `:shared:detekt`; typed `detektJvmMain`, `detektJvmTest`, Android debug/release and unit-test variants, iOS arm64/simulator main/test, metadata main/test tasks.
- Compose: aggregate `:composeApp:detekt`; typed `detektDesktopMain`, `detektDesktopTest`, Android debug/release and unit-test variants, iOS arm64/simulator main/test, metadata main/test tasks.
- `:shared:detekt` and `:composeApp:detekt` report `NO-SOURCE`; they are not source-set coverage.
- Explicit typed task results: shared JVM and iOS/Android tasks report `NO-SOURCE`; Compose iOS test and Android unit-test tasks report `NO-SOURCE`.

## Baseline Findings

Commands:

- `./gradlew :backend:detekt :shared:detekt :composeApp:detekt --no-daemon`
  - backend fails with 257 weighted issues: 71 `UnusedImports`, 55 `LabeledExpression`, 31 `LongMethod`, 30 `StringLiteralDuplication`, 26 `LongParameterList`, 10 `LargeClass`, 9 `ForbiddenSuppress`, 9 `CognitiveComplexMethod`, 7 `ComplexCondition`, 2 `SwallowedException`, 2 `ForbiddenClassName`, 2 `DataClassShouldBeImmutable`, 1 `CyclomaticComplexMethod`, and 1 `ForbiddenImport`.
  - shared and Compose aggregate tasks are `NO-SOURCE`.
- `./gradlew :shared:detektJvmMain :shared:detektJvmTest :composeApp:detektDesktopMain :composeApp:detektDesktopTest --no-daemon`
  - Compose Desktop main fails with 30 weighted issues; Desktop test fails with 1 `UnusedImports` issue.
- `./gradlew :shared:detektIosArm64Main :shared:detektIosArm64Test :composeApp:detektIosArm64Main :composeApp:detektIosArm64Test --no-daemon`
  - shared iOS main/test and Compose iOS test are `NO-SOURCE`; Compose iOS main fails with 6 weighted issues.
- `./gradlew :shared:detektAndroidDebug :shared:detektAndroidDebugUnitTest :composeApp:detektAndroidDebug :composeApp:detektAndroidDebugUnitTest --no-daemon`
  - shared Android main/test are `NO-SOURCE`; Compose Android unit test is `NO-SOURCE`; Compose Android main fails with 14 weighted issues, including `TooGenericExceptionCaught` and `SwallowedException` in `TokenStore.android.kt`.

Representative baseline findings are existing unused imports, forbidden suppressions for `UNCHECKED_CAST` and `TooGenericExceptionCaught`, generic/swallowed exception handling, mutable data-class state, long/complex Compose functions, and naming/string-duplication findings. No findings were fixed.

## Gate Coverage

- Pre-commit runs staged ktlint, `:backend:detekt`, `:backend:ktlintCheck`, `:backend:test`, `:shared:compileKotlinJvm`, OpenAPI verification, cleanliness, and PostgreSQL reachability. It does not run typed shared/Compose Detekt tasks.
- CI `quality.yml` runs backend Detekt, backend ktlint/test, shared JVM compilation, cleanliness, and Compose Android/Desktop compilation. It does not run typed shared/Compose Detekt tasks.
- Pre-push classification runs broader gates for gate-sensitive changes; source-set Detekt coverage must be verified from Gradle task registration, not inferred from compile tasks.

## Rule Matrix

| Rule family | Current evidence | Source sets | Risk/disposition |
|---|---|---|---|
| `UnusedImports` | 71 backend and multiple Compose findings | backend, Compose Desktop | Existing active finding; bounded cleanup candidate |
| Complexity/style thresholds | 55 labeled, 31 long-method, 26 long-parameter, 30 string duplication backend; 30 Desktop findings | backend, Compose Desktop | Existing overlay findings; separate from safety policy |
| Exception safety | `ForbiddenSuppress`, `ForbiddenImport`, `SwallowedException`, `TooGenericExceptionCaught` findings | backend, Compose Android | Requires named boundary review; do not blanket-exclude |
| Coroutine safety | Overlay enables global scope, swallowed cancellation, scope receiver, and suspend-flow rules | all intended Kotlin source sets | No complete baseline because typed common/shared tasks are `NO-SOURCE`; source-set wiring prerequisite |
| Type/nullability safety | Overlay enables cast, nullability, mutable-property, and exhaustive-when rules | all intended Kotlin source sets | Validate type-resolution task coverage before enforcement |
| Placeholder/reflection policy | Overlay enables `NotImplementedDeclaration`, `ForbiddenComment`, `ForbiddenImport`, `ForbiddenMethodCall`, and `ForbiddenSuppress` | all intended Kotlin source sets | Preserve upstream settings; classify real boundary exceptions individually |

## Deviations And Next Step

- No upstream YAML deviation exists today.
- Current configuration is not safe to claim as complete enforcement: aggregate KMP tasks are `NO-SOURCE`, typed coverage is incomplete, and the existing overlay produces 257 backend findings before any rollout cleanup.
- Next child: safety-policy configuration. It must inventory each upstream safety rule against actual source-set task coverage, separate safety from complexity/style cleanup, and add no broad exclusions or baseline.

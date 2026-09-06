# DevMain test-scope dev seeder entry point

Moved `DevSeeder` from `src/main` to `src/test` and introduced `DevMain.kt` (also in `src/test`) as the dev entry point for `./gradlew :backend:run`. Key decisions:

**DevMain as separate entry point, not reflection in Main.kt.** The original spec envisioned `Main.kt` importing `DevSeeder` from test scope, assuming test sources are on the compile classpath during `main()` development runs. Gradle's standard source-set model does not work this way — `src/test` classes are not available to `src/main` compilation. Three options were evaluated:

1. **DevMain.kt in src/test** — separate entry point that seeds then delegates to `MainKt.main()`. The `run` task is overridden to use test classpath. Production `main()` stays clean.
2. **Command-line flag with reflection** — pass `--seed-dev`, conditionally call `Class.forName`. Kept `main()` as single entry point but added reflection.
3. **Reflection in try/catch** — same as above but silent fallback. Handoff called this "ugly."

Chose option 1: cleanest separation, production artifacts contain zero seeding code, and the dev experience (`./gradlew :backend:run`) remains the same.

**`main(config: AppConfig)` overload.** Extracted so `DevMain.kotlin` can call into production main after seeding without re-parsing environment configuration. The no-arg `main()` delegates to it. This is a minor restructuring to support the split entry-point pattern.

**DatabaseTestHelper migrated to AppConfig.parse().** The package-level `dotenv` val in `DatabaseConfig.kt` was only used by `DatabaseTestHelper`. Migrated to `AppConfig.parse()` (which wraps dotenv internally), then removed the dead val and its import. For `TEST_DB_NAME` (not in `AppConfig`), used `System.getenv()` directly.

**build.gradle.kts `run` task override.** Uses `sourceSets["test"].runtimeClasspath` and `mainClass = DevMainKt`. The `runWithJfr`/`runWithJfrAllocation` tasks are unchanged and still use production-only classpath.

## Amendment (#568): dedicated `dev` source set retires the test-scope compromise

The test-classpath coupling this ADR accepted became the exact problem: `./gradlew
:backend:run` compiled every unrelated test source and shipped JUnit-adjacent
test output on the dev server's classpath, while production `AppConfig` parsed
fixture-only credentials (`TEST_*`, `SCOPED_*`, `RELIEF_*`) for seeding that
lives outside production.

- **New `dev` source set (`backend/src/dev/kotlin`)** owns `DevMain.kt`,
  `DevSeeder.kt`, and the new `DevFixtureConfig` (same
  `com.companyb.companyapp.seeding` package). `run` now rides
  `sourceSets["dev"].runtimeClasspath` (main+dev only); `--dry-run` shows
  `compileKotlin` + `compileDevKotlin` with no `compileTestKotlin`, and
  `devRuntimeClasspath` resolves zero JUnit artifacts.
- **Fixture credentials leave production `AppConfig`.** `DevFixtureConfig`
  parses the same six env keys with the same blank-means-absent rule; `DevMain`
  composes `AppConfig.parse()` + `DevFixtureConfig.parse()` (both read dotenv
  with identical precedence from the same root workingDir) and seeds before
  delegating to production `main(config)`. `AUTH_DUMMY_PASSWORD` stays in
  production config — it feeds `Password` timing protection, not fixtures.
- **No widened production visibility.** The seeder writes through `internal`
  stores (`UserRepository`, `UserCapabilityTable`, `RoleTable`,
  `UserRoleTable`, `UserBranchAssignmentTable`, `SessionBaseRateTable`,
  `CapabilityRepository`); the `dev` compilation is a Gradle friend of `main`
  via `associateWith`, and `test` is a friend of `dev` (fixture IDs stay
  `internal`) with `testImplementation(sourceSets["dev"].output)` as the
  explicit wiring seeder tests exercise. Those stores stay `internal` —
  nothing was made public to accommodate the move.
- **Artifacts unchanged in shape.** `installDist`/Docker build from the
  main-only classpath as before (verified: no seeding classes, no JUnit, no
  fixture fields in the packaged `AppConfig`); `runWithJfr*` stay
  production-only. New dev-only code meets production lint scope
  (`MagicNumber`/`ReturnCount` apply under `src/dev`): the horizon literal
  became a named const and the credential guard is single-return.
- Seeded users, capabilities, dotenv precedence, seed idempotency, and the
  k6/`.env.example` env contract are byte-for-byte the old behavior — only
  the holder moved.

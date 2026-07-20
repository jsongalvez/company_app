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

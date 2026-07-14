# AGENTS.md

Kotlin Multiplatform project: backend API server + Compose Multiplatform client (Android/Desktop/iOS).

## Module boundaries

- `backend/` — Javalin + Exposed (Postgres) API server. **Read `backend/AGENTS.md` before touching any backend code.** It is the authority on backend conventions, database access, auth, testing, and performance.
- `composeApp/` — Compose Multiplatform UI. Targets: Android, desktop (JVM), iOS.
- `shared/` — Kotlin Multiplatform shared library (all targets). Serialization-only currently.

All modules depend on `:shared`. The backend depends on nothing else beyond `:shared`.

## Commands

```bash
# One-time setup
bash scripts/setup-hooks.sh          # installs git hooks (core.hooksPath = .githooks)
cp .env.example .env                  # then fill in values

# Docker (Postgres 18)
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml down -v   # teardown + wipe data

# Format (auto-fix all subprojects)
./gradlew ktlintFormat
./gradlew ktlintCheck                 # check only

# Backend quality gate (lint + detekt + test + JMH compilation)
./gradlew :backend:detekt :backend:ktlintCheck :backend:test :backend:jmhClasses

# Run backend (requires Postgres at DB_HOST:DB_PORT, workingDir = repo root for .env)
./gradlew :backend:run

# JMH benchmarks
./gradlew :backend:jmh
```

## Git hooks (CRITICAL)

After `bash scripts/setup-hooks.sh`:

- **pre-commit** runs ktlintFormat (auto-fix + re-stage), then `:backend:detekt :backend:ktlintCheck :backend:test :backend:jmhClasses`, verifies Postgres is reachable, and boots the app to confirm it starts. Commits are blocked if any step fails.
- **pre-push** runs full JMH suite and reminds you to compare against `backend/jmh-baselines.md` via `bash scripts/check-baselines.sh`.

## Configuration details

- `.env` at repo root is loaded by dotenv-kotlin. Gradle `workingDir` for both `run` and `test` tasks is explicitly set to `rootProject.projectDir`.
- Gradle configuration cache and build cache are enabled (`gradle.properties`).
- ktlint + detekt applied to all subprojects via root `build.gradle.kts` `subprojects {}`. Detekt config: `config/detekt/detekt.yml`. Plugin: `detekt-formatting`.
- EditorConfig: 4-space indent, 120-char max line for Kotlin, no-wildcard-imports disabled.

## Performance

The pre-push hook runs JMH benchmarks. Regressions >20% from `backend/jmh-baselines.md` should be investigated before pushing. See `backend/AGENTS.md` for the full performance workflow (measureTimedValue, JFR profiling, k6 load testing, threshold tuning procedure).

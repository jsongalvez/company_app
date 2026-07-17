# AGENTS.md

Kotlin Multiplatform project: backend API server + Compose Multiplatform client (Android/Desktop/iOS).

## Module boundaries

- `backend/` — Javalin + Exposed (Postgres) API server. **Read `backend/AGENTS.md` before touching any backend code.** It is the authority on backend conventions, database access, auth, testing, and performance.
- `composeApp/` — Compose Multiplatform UI. Targets: Android, desktop (JVM), iOS.
- `shared/` — Kotlin Multiplatform shared library (all targets). Serialization, domain types, and route constants.

All modules depend on `:shared`. The backend depends on nothing else beyond `:shared`.

## Commands

```bash
# One-time setup
bash scripts/setup-hooks.sh          # installs git hooks + ktlint CLI (for staged-only formatting)
cp .env.example .env                  # then fill in values

# Docker (Postgres 18)
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml down -v   # teardown + wipe data

# Format (auto-fix all subprojects)
./gradlew ktlintFormat
./gradlew ktlintCheck                 # check only

# Backend quality gate (lint + detekt + test)
./gradlew :backend:detekt :backend:ktlintCheck :backend:test

# JMH benchmarks + baseline check (runs actual benchmarks, not just compile)
./gradlew :backend:jmh

# Check JMH baselines against saved scores
bash scripts/check-baselines.sh

# Run backend (requires Postgres at DB_HOST:DB_PORT, workingDir = repo root for .env)
./gradlew :backend:run

# JMH benchmarks
./gradlew :backend:jmh
```

## Git hooks (CRITICAL)

After `bash scripts/setup-hooks.sh`:

- **pre-commit** runs ktlintFormat (scoped to staged `.kt`/`.kts` files; falls back to project-wide if `ktlint` CLI not on PATH), then `:backend:detekt :backend:ktlintCheck :backend:test`, runs full JMH benchmarks + baseline check, verifies Postgres is reachable, and boots the app to confirm it starts. Commits are blocked if any step fails.
- **pre-push** runs full JMH suite and reminds you to compare against `backend/jmh-baselines.md` via `bash scripts/check-baselines.sh`.

## Configuration details

- `.env` at repo root is loaded by dotenv-kotlin. Gradle `workingDir` for both `run` and `test` tasks is explicitly set to `rootProject.projectDir`.
- Gradle configuration cache and build cache are enabled (`gradle.properties`).
- ktlint + detekt applied to all subprojects via root `build.gradle.kts` `subprojects {}`. Detekt config: `config/detekt/detekt.yml`. Plugin: `detekt-formatting`.
- EditorConfig: 4-space indent, 120-char max line for Kotlin, no-wildcard-imports disabled.

## composeApp logging convention

All composeApp code uses `expect/actual Log` functions from `com.companyb.companyapp.util`:
- `logDebug(tag, message)`, `logInfo(tag, message)`, `logWarn(tag, message)`, `logError(tag, message, throwable?)`
- Desktop → SLF4J/logback, Android → android.util.Log, iOS → println with timestamp prefix.
- **Tag naming**: `"[Feature]VM"` for ViewModels (e.g. `"BranchVM"`, `"SessionVM"`), screen name for composables (`"LoginScreen"`, `"HomeScreen"`), `"TokenStore"`, `"ApiClient"`.
- **Where to log**: method entry, API call start (with endpoint path), success/failure, and catch blocks.
- `logWarn`: handled business errors (HTTP 4xx responses, `UiState.Error` branches in screens — not exceptions).
- `logError` must be used in every `catch` block with the exception as the third arg.
- New ViewModels/screens must follow this convention.

### ApiCallHandler

ViewModels must use `ApiCallHandler` (`com.companyb.companyapp.viewmodel`) for all API calls instead of writing inline `try/catch/log/state` boilerplate:

```kotlin
class ExampleViewModel(private val apiClient: ApiClient) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ExampleVM")

    fun loadData() {
        handler.launch(
            state = _data,
            operation = "loadData",
            endpoint = "GET /api/example",
            block = { apiClient.httpClient.get("/api/example") },
            transform = { it.body() },
        )
    }

    fun performAction() {
        handler.launchUnit(
            state = _actionResult,
            operation = "performAction",
            endpoint = "POST /api/example/action",
            block = { apiClient.httpClient.post("/api/example/action") },
        )
    }
}
```

- `launch`: for calls that deserialize a response body. Use `transform = { it.body() }` for standard deserialization.
- `launchUnit`: for calls that only need success/failure status (no response body).
- `entryMessage`: optional parameter to customize the entry log (defaults to `"$operation called"`).
- The handler automatically manages `UiState.Loading`, `UiState.Success`, `UiState.Error`, and all logging (`logInfo` for lifecycle, `logError` for exceptions).
- ViewModels that use `ApiCallHandler` exclusively do not need to import `logInfo`, `logError`, or `launch` from kotlinx.coroutines.

## Performance

The pre-push hook runs JMH benchmarks. Regressions >20% from `backend/jmh-baselines.md` should be investigated before pushing. See `backend/AGENTS.md` for the full performance workflow (measureTimedValue, JFR profiling, k6 load testing, threshold tuning procedure).

## Ticket tracking

When completing a ticket via `/implements`:

1. Append `**Status:** ✅ done` to the bottom of the ticket file.
2. Mark `[x]` on the ticket's row in the corresponding `TRACKING.md` (`.scratch/*/issues/TRACKING.md`).

## Agent skills

### Issue tracker

GitHub Issues on `jsongalvez/company_app`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: `CONTEXT.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.

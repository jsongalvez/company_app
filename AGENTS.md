# AGENTS.md

Kotlin Multiplatform project: backend API server + Compose Multiplatform client (Android/Desktop/iOS).

## Module boundaries

- `backend/` — Javalin + Exposed (Postgres) API server. **Read `backend/AGENTS.md` before touching any backend code.** It is the authority on backend conventions, database access, auth, testing, and performance.
- `composeApp/` — Compose Multiplatform UI. Targets: Android, desktop (JVM), iOS. **Read `composeApp/AGENTS.md` for UI conventions, logging, and ViewModel patterns.**
- `shared/` — Kotlin Multiplatform shared library (all targets). Serialization, domain types, and route constants. **Read `shared/AGENTS.md` for shared module conventions.**

All modules depend on `:shared`. The backend depends on nothing else beyond `:shared`.

## Agent workflow — starting a task

When a fresh agent opens a GitHub issue to work on:

1. Read the issue body to understand the task
2. Read `CONTEXT.md` for domain vocabulary (use these terms exactly)
3. Read `AGENTS.md` + the relevant module `AGENTS.md` (`backend/`, `composeApp/`, or `shared/`)
4. Scan `docs/adr/` for decisions in the area you're touching
5. Open the relevant doc from the Document Map below (e.g. `docs/architecture.md` for layering, `docs/engines.md` for pseudocode, `docs/business-requirements.md` for rules)
6. Load the skill indicated by the workflow (`/implement`, `/code-review`, etc.)

## Code review — phased loop

Every `/implement` ticket gets a **phased review loop** (adopted in session 38 after a round-3 sanity pass caught 7 issues rounds 1+2 missed — the two-round structure reviewed the same lens twice; distinct lenses + a real exit condition fix the class). The loop runs until **one full pass reports zero HARD findings**.

A **pass** = four phases, each a different review mode, run as parallel `/code-review` sub-agents per phase (Standards + Spec axes default; the phase templates in `docs/agents/code-review-loop.md` slot into them):

1. **P1 Spec conformance** — full delta vs the ticket line-by-line: missing / partial / scope-creep / wrong-implementation, quoted spec lines.
2. **P2 Standards + constraints** — full delta vs documented standards **and every constraint source the code consumes** (theme mappings, shared DTOs/enums, ApiCallHandler contract, ADR axes, both platform actuals, k6 conventions) — constraint files often live outside the diff.
3. **P3 Behavior trace** — end-to-end state machines on the **composed tree**: every user flow + error path *including repeated attempts* (attempt-1 fail → attempt-2 semantics) and the nav back-stack; each flow PASS/FAIL with evidence.
4. **P4 Adversarial edges** — what breaks it: races/orderings, double-taps, stale state after clear/cancel, empty/zero states, dead branches, non-exhaustive `when`s, unmapped theme slots.

**Loop mechanics:**

- **Review points**: batch-fix commits per pass; each pass diffs `git diff <last-pass-commit>`. Untracked files are handed to the sub-agents explicitly on the first pass.
- **Triage**: HARD (bug / regression / security / documented-standard breach / **lesson-class register match** — register in `docs/agents/code-review-loop.md`) → must fix, loop continues. SOFT (smell / judgement call) → fix if cheap; else accept **with a logged reason**; ≤3 accepted SOFTs per pass; the accepted list is handed to the next pass with "re-examine from your angle and re-rate upward if HARD-class from your lens" — acceptance is never load-bearing (round-1 SOFTs became round-3 HARDs).
- **Exit**: one full pass with zero HARD findings across all four phases. Converges naturally — each pass's delta is fixes only (typically 2–3 passes for a build ticket).
- Agents are never told "previous rounds passed" as authority; each pass re-derives flows from the ticket.

The review points + each pass's outcome are recorded in the resolution comment (e.g. "#140 — pass 3 (P3 flow-2 catch) → pass 4: 0 HARD, 2 accepted SOFTs").

## Document map

This repo follows the single-context layout: `CONTEXT.md` (domain glossary) + `docs/adr/` (architecture decisions). Below is a quick-reference for where to find what.

| When you need... | Read this |
|-----------------|-----------|
| Domain terms and glossary | `CONTEXT.md` |
| Architecture, tech stack, layering, deep module map | `docs/architecture.md` |
| Business rules and domain terminology (detailed) | `docs/business-requirements.md` |
| Engine pseudocode (commission, delegate, remittance) | `docs/engines.md` |
| Architecture decisions | `docs/adr/` (numbered 0001-0015) |
| Feature specs | `docs/specs/` |
| Backend conventions (Exposed, routes, auth, testing, Javalin) | `backend/AGENTS.md` |
| Frontend conventions (logging, ViewModels, design tokens) | `composeApp/AGENTS.md` |
| Shared module conventions (domain types, DTOs, serialization) | `shared/AGENTS.md` |
| Issue tracking | `docs/agents/issue-tracker.md` |
| Triage labels | `docs/agents/triage-labels.md` |
| Performance baselines | `backend/jmh-baselines.md` |
| Load test results | `tests/k6/results/baseline-results.md` |

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

- **pre-commit** runs ktlintFormat (scoped to staged `.kt`/`.kts` files; falls back to project-wide if `ktlint` CLI not on PATH), then `:backend:detekt :backend:ktlintCheck :backend:test`, test-data cleanliness check, `:shared:compileKotlinJvm`, and verifies Postgres is reachable. Commits are blocked if any step fails.
- **pre-push** runs test-data cleanliness check, composeApp multi-target compilation (desktop + Android + iOS), and k6 load-test baseline. JMH no longer runs on push — it lives in CI (`.github/workflows/jmh.yml`, backend-touching pushes + merge to master; re-runs once on a suspected regression, fails only on a confirmed two-run regression). Takes ~3 min — always run `git push` with a sufficient timeout (600000 ms).

## Configuration details

- `.env` at repo root is loaded by dotenv-kotlin. Gradle `workingDir` for both `run` and `test` tasks is explicitly set to `rootProject.projectDir`.
- Gradle configuration cache and build cache are enabled (`gradle.properties`).
- ktlint + detekt applied to all subprojects via root `build.gradle.kts` `subprojects {}`. Detekt config: `config/detekt/detekt.yml`. Plugin: `detekt-formatting`.
- EditorConfig: 4-space indent, 120-char max line for Kotlin, no-wildcard-imports disabled.

## composeApp

See `composeApp/AGENTS.md` for UI conventions, logging, ViewModel patterns, and the ApiCallHandler.

## Performance

JMH benchmarks run in CI (`.github/workflows/jmh.yml`) on backend-touching pushes and merge to master. Regressions exceeding the per-benchmark threshold (default 20%; 40% for noise-sensitive nanosecond-scale benchmarks like `BranchDayBenchmark.*`) from `backend/jmh-baselines.md` are flagged; the check fails only when a regression reproduces across two runs. JMH scores in `backend/jmh-baselines.md` were measured on a dev machine — after any CI-runner baseline shift, re-establish by copying the first CI run's scores into the file. See `backend/AGENTS.md` for the full performance workflow (measureTimedValue, JFR profiling, k6 load testing, threshold tuning procedure).

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

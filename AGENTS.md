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

## Human Decisions

Never ask the user a question or invoke the question tool. When business behavior,
scope, safety, external authorization, or preference needs human input, create a
separate issue with `needs-info` or `ready-for-human`, record verified facts and the
blocking decision, and continue unrelated AFK work. If no safe continuation exists,
record the blocker in the handoff and stop. Never guess.

## Code review — risk-based graph

Implementation work uses a review profile matched to blast radius. Review work is a dependency graph, not fixed ceremony: independent read-only lanes run in parallel, one writer applies a coherent fix batch, and only affected checks rerun.

- **Fast profile** — docs, configuration, tests, and sub-30-minute mechanical fixes: targeted validation and focused review. Gates are skipped when `docs/agents/gates.md` permits it.
- **Standard profile** — normal implementation: parallel P1 Spec, P2 Standards, P3 Behavior, and P4 Adversarial review once; fix HARD findings; rerun only lenses affected by the fix; stop when required lenses report zero HARD and no unadjudicated ESCALATE.
- **High-risk profile** — auth, finance, migrations, concurrency, shared contracts, `commonMain`, `expect`/`actual`, Gradle, or cross-module interfaces: standard profile plus earlier full validation and targeted architecture review. Run full P5 only when risk or evidence warrants it.

P1-P4 remain available as independent review packets:

1. **P1 Spec conformance** — ticket requirements, missing/partial behavior, scope creep, and false claims.
2. **P2 Standards + constraints** — documented standards and every constraint source consumed by changed code.
3. **P3 Behavior trace** — composed-tree flows, error paths, repeated attempts, lifecycle, and back-stack.
4. **P4 Adversarial edges** — races, stale state, empty states, dead branches, unmapped slots, and format coupling.

P5 is not a mandatory exit phase. Cheap hygiene checks run with each fix batch. Architecture-depth review triggers when the delta changes an interface, seam, shared state, layering, or creates repeated structure; findings become separate architecture tickets or cheap in-ticket fixes. Full P5 can run for high-risk deltas or when focused review finds architecture residue.

**Graph mechanics:**

- Parallel agents are read-only unless they own disjoint isolated work. One writer/integrator owns production edits, formatting, compilation, and commits.
- A ticket may span multiple sessions. Handoffs record claimed ticket, phase, last verified commit, evidence, blockers, and next action. Handoff before context becomes crowded; stay below 150k tokens.
- Review agents do not run expensive aggregate builds. The writer runs targeted checks after a fix batch and one full compile/test gate at integration. High-risk changes escalate earlier.
- HARD findings, security issues, regressions, data loss, documented breaches, and lesson-class matches must be fixed. SOFT findings need a logged disposition. Unresolved ESCALATE findings block exit.
- Each pass re-derives behavior from the ticket and composed tree; previous passes are evidence, never authority.

Record selected profile, review lanes, fix batches, validation, skipped checks, accepted SOFTs, and architecture findings in the resolution comment.

## Decision loop — deferred human review

Grilling / wayfinder human-review tickets run the decision-loop discipline before a design reaches the human — `docs/agents/decision-loop.md`: five parallel lenses (fact integrity, domain coherence, long-term architecture, falsification, comprehension), HARD/SOFT triage, exit on one full zero-HARD pass. Decisions are delivered asynchronously through tracker issues, never through questions in-session.

Architectural choices are agent-owned by default. Do not ask the user to choose between implementation shapes, modules, seams, abstractions, or review dispositions when business requirements and existing constraints are clear. Select the strongest evidence-backed design, record important rationale in the ticket or ADR, and proceed. When business behavior, scope, safety, external authorization, or explicit preference is ambiguous, defer it as a separate appropriately labeled tracker issue and continue only with safe independent work.

## Document map

This repo follows the single-context layout: `CONTEXT.md` (domain glossary) + `docs/adr/` (architecture decisions). Below is a quick-reference for where to find what.

| When you need... | Read this |
|-----------------|-----------|
| Domain terms and glossary | `CONTEXT.md` |
| Architecture, tech stack, layering, deep module map | `docs/architecture.md` |
| Business rules and domain terminology (detailed) | `docs/business-requirements.md` |
| Engine pseudocode (commission, delegate, remittance) | `docs/engines.md` |
| Architecture decisions | `docs/adr/` (authoritative directory; inspect each relevant ADR's status, supersedes, and amends fields) |
| Feature specs | `docs/specs/` |
| Backend conventions (Exposed, routes, auth, testing, Javalin) | `backend/AGENTS.md` |
| Frontend conventions (logging, ViewModels, design tokens) | `composeApp/AGENTS.md` |
| Shared module conventions (domain types, DTOs, serialization) | `shared/AGENTS.md` |
| Issue tracking | `docs/agents/issue-tracker.md` |
| Triage labels | `docs/agents/triage-labels.md` |
| Decision-loop lenses + deferred human-review frame | `docs/agents/decision-loop.md` |
| Gate ledger (runnable CHECK/EXPECT acceptance for builds) | `docs/agents/gates.md` |
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
- **Permissions** — `opencode.json`'s `permissions` array at repo root holds the agent's access rules. When you need to know what you may access, or must request a new access, read **only that section** — the plugin/skill/server blocks are unrelated config and don't justify whole-file reads.

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

### Agent-facing docs

When editing any agent-facing markdown — AGENTS.md files, `docs/agents/`, `CONTEXT.md`, ADRs, skills — load `/writing-for-agents` first.

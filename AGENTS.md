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

- **Fast profile** — docs, configuration, tests, and sub-30-minute mechanical fixes: targeted validation and focused review.
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
- Review agents do not run expensive aggregate builds. The writer runs targeted checks after a fix batch; integration is direct-to-master with no mandatory full compile/test gate — asynchronous CI owns broad verification. High-risk changes escalate earlier.
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
| Wayfinding chain daemon — stalls, duplicates, restarts, packet rules | `docs/agents/wayfinder-loop.md` |
| Triage labels | `docs/agents/triage-labels.md` |
| Decision-loop lenses + deferred human-review frame | `docs/agents/decision-loop.md` |
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

# Targeted local validation (map #329) — auto-selects the narrowest warm Gradle
# tasks for the current change; pass gradle args to override. No broad gates.
bash scripts/validate.sh

# Local full-CI replication (#341) — runs the hosted quality.yml gate set detached
# (non-blocking) when CI minutes are unavailable; opt-in diagnostic, never a gate.
bash scripts/local-ci.sh            # launch detached
bash scripts/local-ci.sh --status   # per-gate PASS/FAIL/SKIP/RUNNING

# Format (auto-fix all subprojects)
./gradlew ktlintFormat

# JMH benchmarks + baseline check — manual diagnostics only, never a ticket toll
./gradlew :backend:jmh
bash scripts/check-baselines.sh

# Run backend (requires Postgres at DB_HOST:DB_PORT, workingDir = repo root for .env)
./gradlew :backend:run
```

Local validation is targeted and agent-invoked: run the smallest warm task that answers the
current question, once per meaningful slice — not again at commit/push. Change-type → task
table: `backend/AGENTS.md` ("Targeted validation"). Hooks and CI own no local gates
(map #329).

Future non-merge commits must include `ref #<number>` somewhere in the commit
message. The local `commit-msg` hook enforces this without network access, accepts
closed issue numbers, permits multiple references, and exempts Git merge commits.
Run `bash scripts/setup-hooks.sh` after cloning to install `.githooks`.

## Integration — direct-to-master

Ordinary AFK tickets integrate as well-scoped commits **directly on `master`**, then push
immediately — no feature branch, PR, or merge step (map #329). One ticket = one or more
identifiable commits; `ref #<number>` in every commit message keeps each ticket's commits
revertable.

**PR escalation triggers** (the only reasons to leave direct-to-master):

- the human explicitly requests a PR/review;
- work arrives from an external contributor;
- unusually risky or irreversible integration where isolated review adds real value;
- a future multi-agent/concurrent workflow needs an integration boundary.

High-risk code still uses the high-risk review profile above — that is review depth, not an
integration branch, and never "wait on full CI" (asynchronous CI owns remote verification).

Never commit unrelated dirty work: stage only the ticket's files. Do not reintroduce
long-lived integration branches.

## Git hooks (CRITICAL)

After `bash scripts/setup-hooks.sh`:

- **pre-commit** formats staged `.kt`/`.kts` files with the standalone ktlint CLI (warn + skip when missing — never a Gradle fallback) and runs `bash -n` on staged shell files. It never starts Gradle, Postgres, the backend, k6, JMH, or tests; normal overhead is well under 5 seconds. Compile/static/test coverage is targeted, warm, agent-invoked validation plus asynchronous CI.
- **pre-push** is bookkeeping only: no Gradle, DB, backend startup, OpenAPI build, Compose compile, k6, JMH, Detekt, `ktlintCheck`, warning-as-error compile, or test execution. Full validation runs asynchronously in CI; push network transfer dominates. Run hook and `git push` tool calls with normal short timeouts.

## Configuration details

- `.env` at repo root is loaded by dotenv-kotlin. Gradle `workingDir` for both `run` and `test` tasks is explicitly set to `rootProject.projectDir`.
- Gradle configuration cache and build cache are enabled (`gradle.properties`).
- ktlint + detekt applied to all subprojects via root `build.gradle.kts` `subprojects {}`. Detekt config: `config/detekt/detekt.yml`. Plugin: `detekt-formatting`.
- EditorConfig: 4-space indent, 120-char max line for Kotlin, no-wildcard-imports disabled.
- **Permissions** — `opencode.json`'s `permissions` array at repo root holds the agent's access rules. When you need to know what you may access, or must request a new access, read **only that section** — the plugin/skill/server blocks are unrelated config and don't justify whole-file reads.

## composeApp

See `composeApp/AGENTS.md` for UI conventions, logging, ViewModel patterns, and the ApiCallHandler.

## Performance

JMH is a manual diagnostic (`workflow_dispatch` on `.github/workflows/jmh.yml`), never an
integration gate: ordinary pushes launch nothing, no session waits for it, and a JMH result
cannot block a ticket. Regressions exceeding the per-benchmark threshold (default 20%; 40% for
noise-sensitive nanosecond-scale benchmarks like `BranchDayBenchmark.*`) from
`backend/jmh-baselines.md` are flagged only when someone actually runs it; the check fails only
when a regression reproduces across two runs. Baselines in `backend/jmh-baselines.md` are
per-benchmark medians of repeated clean CI runs — shared-runner scores vary ~1.6× run-to-run, so
never recalibrate from a single run; after any CI-runner baseline shift, dispatch the workflow
several times and recompute the medians. See `backend/AGENTS.md` for the full performance
workflow (measureTimedValue, JFR profiling, k6 load testing, threshold tuning procedure).

CI is asynchronous and budgeted (#333): the active agent never polls it, and a failed run is
the durable repair signal — next-session corrective priority, not a synchronous gate.
**Next-session reconciliation** is the session-start check: one `gh api
repos/jsongalvez/company_app/commits/<latest-master-sha>/check-runs --jq '[.check_runs[] |
select(.conclusion != "success" and .conclusion != null) | .name]'` call (unbilled, no hosted
compute); red = repair first, green/pending = continue under the map. GitHub-hosted Actions
minutes are a constrained monthly budget: the hosted set is capped at **≤300 minutes/month**
(jmh ≤55 manual-dispatch diagnostics; the quality workflow moved to a **self-hosted runner**
(`company-local`, #342) and consumes zero hosted minutes), and hosted schedules beyond this
set need a measured reserved slice of that cap before existing. Ordinary successful tickets
consume near-zero hosted minutes.

## Ticket tracking

GitHub Issues are the canonical ticket store; a closed issue is the completion record.
No tracked per-ticket Markdown ledgers — git history and the resolution comment own
ticket state.

## Agent skills

### Issue tracker

GitHub Issues on `jsongalvez/company_app`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: `CONTEXT.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.

### Agent-facing docs

When editing any agent-facing markdown — AGENTS.md files, `docs/agents/`, `CONTEXT.md`, ADRs, skills — load `/writing-for-agents` first.

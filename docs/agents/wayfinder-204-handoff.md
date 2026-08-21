# Handoff - Architecture Map #180, Session 101

## What this is

Session 101 established that architecture follow-up now has its own map: issue #180. Future wayfinder-loop sessions must load issue #180 as their canonical map. Map #89 is irrelevant to this chain.

## Canonical context pointers

- `CONTEXT.md` — read before design work; it defines domain vocabulary.
- `docs/business-requirements.md` — read before audit or implementation; it defines target behavior.
- `docs/agents/audit-your-codebase.md` — read before every focused or full architecture audit.
- `/improve-codebase-architecture` — run for candidate discovery and HTML reporting.
- `/codebase-design` — use its module, interface, depth, seam, adapter, locality, and leverage vocabulary.
- `docs/agents/issue-tracker.md` — use for Map #180, child, dependency, claim, and resolution operations.
- Relevant module `AGENTS.md` and area ADRs — read before implementation or when evaluating a proposed change.

## Session outcome

- Loaded latest handoff and wayfinder workflow.
- Preserved worktree state; made no production or tracker changes.
- No ticket claimed. No human decision was requested because no decision can be resolved autonomously in this session.

## Review status

- No implementation occurred; phased implementation review was not applicable.

## Verification

- `git status --short --branch`: clean before handoff creation.
- No build or test run; no source changes were made.

## Tracker state

- #180 is the open architecture map and remains open while its child tickets are resolved.
- Issues #181 through #190 are existing child tickets of #180; open children #181-#188 form current frontier, while #189-#190 are closed history.
- #183 requires human enum compatibility policy; do not infer it.
- Migration cost is zero: no users or upstream dependents constrain redesign.

## Critical blockers

- Resolve architecture child tickets under #180; never close the permanent map.
- iOS compilation remains environment-blocked by unavailable Kotlin/Native 2.3.10 Linux aarch64 artifact.
- `.wayfinder-loop.lock` is unrelated state; preserve it.

## How to drive the next session

1. Load issue #180 at low resolution as canonical map; do not load or select Map #89 for this chain.
2. Select first unblocked, unassigned child of #180, or use a user-named child.
3. Claim exactly one child before research or edits. Resolve it under `/wayfinder` rules.
4. Choose review profile: fast for docs/config/tests or sub-30-minute mechanical work; standard for normal implementation; high-risk for auth, finance, migrations, concurrency, shared contracts, `commonMain`, `expect`/`actual`, Gradle, or cross-module interfaces.
5. Parallelize read-only review, research, test planning, and risk analysis. Keep one writer/integrator for production edits, formatting, compilation, and commits; avoid persistent worktrees on the 50 GB VPS.
6. A ticket may span sessions. Handoff before context becomes crowded, below 150k tokens, with claimed ticket, profile, phase, last verified commit, evidence, blockers, and next action.
7. For implementation: reviewers do not run expensive aggregate builds. Run targeted checks after fix batches and one full compile/test gate at integration; escalate earlier for high-risk changes. P5 is optional and risk-triggered.
8. After each implementation child, run focused `/improve-codebase-architecture` review of changed modules, interfaces, call sites, tests, and nearby seams.
9. When no actionable child remains, run a full `/improve-codebase-architecture` audit using `docs/agents/audit-your-codebase.md`.
10. If audit finds justified candidates, choose strongest evidence-backed candidate automatically, create and wire child issue under #180, then continue AFK.
11. If audit finds no justified candidate, ask exactly: `No justifiable architecture candidate found after full audit. Should I continue monitoring, or redirect Map #180?` Then wait. Do not write a handoff; loop must notify human and pause.
12. Keep #180 open forever. Append audit checkpoints as issue comments; keep body as low-resolution index with Decisions-so-far, Not yet specified, and Out of scope.
13. Preserve unrelated worktree state and `.wayfinder-loop.lock`.

## Process source of truth

- Root `AGENTS.md` — active risk-based review graph, profiles, parallelism, context budget, and validation policy.
- `docs/agents/code-review-loop.md` — review packets, finding classes, and optional P5 triggers.
- `docs/agents/gates.md` — build gate sequence and integration validation.
- Issue #180's Permanent Map Contract — destination, constitution, cadence, and no-candidate protocol.

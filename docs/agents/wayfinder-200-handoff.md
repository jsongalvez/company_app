# Handoff - Map #89 (Frontend rebuild), Session 97

## What this is

Session 97 charted AFK architecture-audit task #180 as a child of Map #89 and added native blocking. Map #89 development is paused until audit task #180 resolves.

## Session outcome

- Created [AFK architecture audit: reduce code and harden future-agent seams](https://github.com/jsongalvez/company_app/issues/180).
- Linked task #180 as a GitHub sub-issue of [Map: Frontend rebuild - from scratch to fully-integrated UI](https://github.com/jsongalvez/company_app/issues/89).
- Added native dependency: Map #89 is blocked by task #180.
- Task is fully AFK and unclaimed. No product code or architecture behavior changed.
- Gist audit discipline incorporated: coverage contract, bounded non-overlapping subsystem reviews, independent audit validation, explicit skips, materiality threshold, priority/dependency ranking, and durable lesson ledger.

## Task constitution

- Reduce code count when deletion concentrates complexity rather than relocating it.
- Improve module depth, locality, leverage, seams, ownership, state representation, lifecycle, and concurrency behavior.
- Prefer boring local code over speculative abstractions.
- Make the whole repository safer for future agents and future changes one year from now.
- Spare no effort on coverage, falsification, duplicate detection, and validation.
- Keep durable lessons in a repo-tracked ledger consumed by future agents.
- Audit is read-only for product code. Recommendations become separate implementation tasks.

## Review status

- No Map #89 ticket was claimed or resolved.
- No architecture implementation started.
- The architecture audit itself must be executed as one AFK task session: claim #180 before work, then do not start another ticket.

## Verification

- `gh issue view 180`: OPEN, label `wayfinder:task`.
- `gh api repos/jsongalvez/company_app/issues/89/sub_issues?per_page=100`: #180 present as child.
- `gh api repos/jsongalvez/company_app/issues/89/dependencies/blocked_by`: #180 present as open blocker.
- `gh issue view 89`: OPEN.
- No build or product test gate was needed; this session changed only GitHub tracker state and this handoff.

## How to drive the next session

1. Read this handoff and the full body of task #180.
2. Load `/wayfinder`, `/improve-codebase-architecture`, `/codebase-design`, `/grilling`, and `/domain-modeling` as required by the task and workflow.
3. Claim task #180 before any audit work. Treat it as fully AFK: do not ask for design choices; record unresolved human decisions as fog.
4. Read `CONTEXT.md`, root and module `AGENTS.md` files, relevant ADRs, `docs/architecture.md`, and the task's external audit reference.
5. Establish complete repository subsystem coverage before reviewing candidates. Audit `composeApp/`, `backend/`, `shared/`, platform bridges, generated contracts, migrations, tests, scripts, hooks, CI, and materially relevant documentation.
6. Keep audit read-only for product code. Write only canonical audit report, lesson ledger, and required agent-facing pointers. Create separate implementation tickets for accepted recommendations.
7. Validate every recommendation independently. Record explicit skips, rejected recommendations, duplicates, superseded findings, dependencies, deletion-test results, and confidence.
8. Do not resume Map #89 development until #180 is resolved or a human explicitly overrides the native blocker.
9. Finish by writing `docs/agents/wayfinder-201-handoff.md`; stop after writing it.

## Critical blockers

- Map #89 is intentionally blocked by open task #180.
- Task #180 has no human blocker; it is AFK.
- k6 v2.2.0 is installed at `/usr/local/bin/k6` and verified on `linux/arm64`; future pre-push runs can execute the load-test baseline.
- `.wayfinder-loop.lock` is unrelated untracked state; preserve it.

## Suggested skills for next session

- `/wayfinder` - claim and drive task #180.
- `/improve-codebase-architecture` - execute full read-only architecture audit.
- `/codebase-design` - use module/interface/depth/seam/adapter/locality/leverage vocabulary.
- `/writing-for-agents` - required before next handoff or agent-facing ledger/report edit.

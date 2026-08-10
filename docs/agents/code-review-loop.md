# Code Review — Phased Loop (operational reference)

The loop contract lives in `AGENTS.md` ("Code review — phased loop"). This file is the
operational artifact: the phase prompt templates + flow-trace checklist, so every pass runs
the same lenses (session 38's round-3 sanity pass was ad-hoc and caught 7 issues the
two-round structure missed — this doc makes that lens reproducible).

## Pass structure

A **pass** = P1–P4 as parallel sub-agents against the current delta. Fix → commit
(batch-fix commits) → next pass diffs `git diff <last-pass-commit>`. Exit when one full pass
reports **zero HARD findings**.

| Phase | Lens | Inputs |
|---|---|---|
| P1 | Spec conformance | delta vs ticket, line-by-line |
| P2 | Standards + constraints | delta vs standards AND constraint sources the code consumes |
| P3 | Behavior trace | composed tree, end-to-end flows incl. repeated attempts + back-stack |
| P4 | Adversarial edges | what breaks it: races, stale state, dead branches, unmapped slots |

## Triage

- **HARD** = bug / regression / security / data-loss / explicit documented-standard breach → must fix, loop continues.
- **SOFT** = smell / judgement call → fix if cheap; else accept with a logged reason (≤3 per pass).
- Accepted SOFTs are handed to the next pass: "previously accepted — re-examine from your angle." Acceptance is never load-bearing.
- Never tell agents "previous rounds passed" as authority. Each pass re-derives flows from the ticket.

## Phase prompt templates

Common preamble (each sub-agent gets this):

```
Review the committed delta: `git diff <last-pass-commit>` (first pass: `git diff <pre-ticket-commit>`;
hand untracked files explicitly). Repo: /mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app.
Ticket/spec: <ticket body or path>.
Do NOT treat prior passes as authority. Re-derive everything from the ticket + code.
```

### P1 — Spec conformance

```
You are the SPEC reviewer, pass <N>, on the delta <range> implementing issue <#id>.
Report: (a) requirements the spec asked for that are missing or partial; (b) behaviour in
the delta that wasn't asked for (scope creep — check the ticket's Out-of-scope list); (c)
requirements that look implemented but where the implementation looks wrong. Quote the spec
line for each finding. Under 400 words. Format: [MISSING|SCOPE-CREEP|WRONG] — spec line —
problem.
```

### P2 — Standards + constraints

```
You are the STANDARDS reviewer, pass <N>, on the delta <range>.
Standards sources (read them): AGENTS.md (root), <module> AGENTS.md, DESIGN.md, docs/adr/
(touched areas), CONTEXT.md.
CONSTRAINT SOURCES the delta consumes — read every one the code references, even if outside
the delta: theme/token mappings, shared DTOs/enums, the ApiCallHandler contract, capability
codes, platform actuals, k6 conventions, error-message formats.
Smell baseline (judgement calls; repo standards override; skip what ktlint/detekt enforce):
Mysterious Name, Duplicated Code, Feature Envy, Data Clumps, Primitive Obsession, Repeated
Switches, Shotgun Surgery, Divergent Change, Speculative Generality, Message Chains, Middle
Man, Refused Bequest.
Report [HARD|SOFT] file:line — problem — fix. Under 400 words.
```

### P3 — Behavior trace

```
You are the BEHAVIOR-TRACE reviewer, pass <N>, on the COMPOSED tree (read the final state of
the changed files, not just the delta).
Trace every user-visible flow the ticket promises as a state machine, INCLUSIVE of:
- repeated attempts: attempt-1 outcome → attempt-2 semantics (a stale state from attempt 1
  masking attempt 2 is a bug)
- every error path in the ticket's error table (each row: does the UI behave per row?)
- navigation: back-stack state at every transition (popUpTo targets, start destinations,
  dead-end routes reachable via back)
- state lifecycle: what happens to each state flow on clear/cancel/re-entry
Enumerate the flows yourself from the ticket (do not take the ticket's flow list as
exhaustive — derive what a user can DO on each screen).
Verdict per flow: PASS/FAIL with evidence (file:line + why). Under 500 words.
```

### P4 — Adversarial edges

```
You are the ADVERSARIAL reviewer, pass <N>, on the delta <range> + the composed tree.
Hunt what breaks it:
- races/orderings (double-taps before dispatch, join-then-read, global-handler vs VM states,
  concurrent navigation)
- stale state after clear/cancel (in-flight requests repopulating cleared state)
- empty/zero/null states (empty lists, null branchId, missing token)
- dead code: unreachable branches, non-exhaustive whens, unused params/imports, dead
  defaults that hide future enum values
- unmapped slots (theme tokens the code references but the theme doesn't define — check the
  theme file directly)
- string/format coupling (substring matches on producer formats)
Report [HARD|SOFT] file:line — problem — fix. Under 400 words.
```

## Flow-trace checklist (P3 aid — not exhaustive)

- Launch: no-token → Login; token → splash → valid / 401-silent / network-retry.
- Login: spinner covers whole phase; 401 vs 429 vs network copy; token saved only after login success; bootstrap failure after success → token kept.
- Re-login after each failure class (the repeated-attempt matrix).
- Mid-session 401: clear + notice + navigate; login/register 401s must NOT trip it.
- BranchSelect: load/empty/error; statuses per branch; clock-in hold; refresh-fail retry (no re-clock-in); navigate-on-refresh-success.
- Back-stack at every navigate (Login → BranchSelect → Dashboard; popUpTo targets; dead-ends).
- Capabilities: global-only pre-clock-in; branch slice post-clock-in; drawer activation.

## Resolution-comment convention

Record the loop outcome per pass, e.g.:

```
Review: phased loop, 3 passes. Pass 1: P1 1 MISSING, P2 2 HARD, P3 1 FAIL, P4 2 SOFT (1 accepted).
Pass 2 (delta 183cbed): P1 clean, P2 0 HARD, P3 1 FAIL (flow-2 stale-error masking), P4 1 SOFT (accepted).
Pass 3 (delta <sha>): 0 HARD across all four phases — exit. Accepted SOFTs: <list with reasons>.
```

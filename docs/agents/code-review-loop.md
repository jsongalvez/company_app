# Code Review — Phased Loop (operational reference)

The loop contract lives in `AGENTS.md` ("Code review — phased loop"). This file is the
operational artifact: the phase prompt templates + flow-trace checklist + **lesson-class
register**, so every pass runs the same lenses (session 38's round-3 sanity pass was ad-hoc
and caught 7 issues the two-round structure missed — this doc makes that lens reproducible).

## Pass structure

A **pass** = P1–P4 as parallel sub-agents against the current delta. Fix → commit
(batch-fix commits) → next pass diffs `git diff <last-pass-commit>`. Exit when one full pass
reports **zero HARD findings and no unadjudicated ESCALATEs** (triage empties the bucket
before exit).

| Phase | Lens | Inputs |
|---|---|---|
| P1 | Spec conformance | delta vs ticket, line-by-line |
| P2 | Standards + constraints | delta vs standards AND constraint sources the code consumes |
| P3 | Behavior trace | composed tree, end-to-end flows incl. repeated attempts + back-stack |
| P4 | Adversarial edges | what breaks it: races, stale state, dead branches, unmapped slots |

## Classification

### Finding buckets

- **HARD** = bug / regression / security / data-loss / explicit documented-standard breach, or a lesson-class register match (below) — must fix, loop continues.
- **SOFT** = smell / judgement call → fix if cheap; else accept with a logged reason (≤3 per pass).
- **ESCALATE** = HARD-class flavor (regression / data / security) whose reachability the phase cannot fully prove. The phase reports it as ESCALATE and **triage adjudicates** — reachability doubt never downgrades a HARD-flavored finding to SOFT; it escalates.

### Lesson-class register

The register is the memory of the loop: documented bug classes that recurred as HARD once and must default HARD thereafter. **A finding matching a registered class is HARD unless the phase (or triage) proves it inert** — the proof burden sits on the acceptor, not the reporter (the rejected-HARDs discipline, reversed). Resolutions append new classes and bump occurrence counts; builds apply the register as much as audits.

| Class | Signature | Origin (occurrences) |
|---|---|---|
| count-0 misfire | a swallowed-write fallback (`insertIgnore` count 0) that returns/derives "existing" by a key too narrow for the constraint class that swallowed (PK vs unique-index vs content) — wrong data served to the caller | #137 r1 HARD; #146 HARD-class (2) |
| truth-class | a claim or doc line contradicted by shipped code — resolution comments, ADR/AGENTS.md/KDoc | #145 (2 HARD); #146 (1 HARD-class); #148 (ticket+KDoc claimed "serialName + optional `?arg={arg}` — verified"; `RouteBuilder` emits non-optional args as `/` path segments) |
| lazy lock | Exposed `forUpdate()` (or any deferred op) without a terminal op silently no-ops — and the docs may stale-claim it unavailable | #136 r2; #146 doc line (2) |
| exact-path gate | a route-level gate written for a path shape the actual route never matches (segment-count drift) — a gate that never fires | #114; #128/#131 (5+); #148 (nav pattern strip written for query-only shapes; non-optional args are `/` path segments) |
| layout starvation | a `fillMaxSize`/`fillMaxHeight`/intrinsic-measure misuse that gives a pinned sibling (button, list, divider) zero height inside a wrap-content parent — UI silently invisible | #144 D5; #147 pass-2 divider (2) |
| fix-that-didn't-land | a claimed fix that never reached the file — imports/params added but the body replacement silently missed (string-mismatch edits); the commit message and the phases disagree — verified by reading the file, never the commit | #147 pass-4 (1) |

### Triage (driving agent, after each pass's phases report)

Triage re-derives **every** finding's class from the phase's own evidence — phase ratings are inputs, never authority (pass-1 phases rated both #146 HARD-class findings SOFT; the synthesis caught them). Done when:

- every finding re-classified from evidence, register matches checked, ESCALATE entries adjudicated (HARD → fix, or rejected with proof of inertness);
- the driving agent itself hunts the register classes in the constraint sources — reads the AGENTS.md/ADR/KDoc lines the delta depends on (the #146 doc contradiction was caught this way, not by a phase);
- accepted SOFTs ≤3, each with a reason, handed to the next pass: "previously accepted — re-examine from your angle **and re-rate upward** if HARD-class from your lens."

## Phase prompt templates

Common preamble (each sub-agent gets this):

```
Review the committed delta: `git diff <last-pass-commit>` (first pass: `git diff <pre-ticket-commit>`;
hand untracked files explicitly). Repo: /mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app.
Ticket/spec: <ticket body or path>.
Check findings against the lesson-class register above — a match defaults HARD unless you prove it inert.
Falsify the spec's claims AND the constraint-doc sentences the surface rests on: quote each doc line,
state code-verified or CONTRADICTED.
Re-derive everything from the ticket + code. Prior passes are evidence to re-check, never authority.
```

### P1 — Spec conformance

```
You are the SPEC reviewer, pass <N>, on the delta <range> implementing issue <#id>.
Report: (a) requirements the spec asked for that are missing or partial; (b) behaviour in
the delta that wasn't asked for (scope creep — check the ticket's Out-of-scope list); (c)
requirements that look implemented but where the implementation looks wrong; (d) claims in
the spec or its source resolutions that are FALSE as shipped — quote the claim, state
code-verified or contradicted with evidence. Quote the spec line for each finding.
Under 400 words. Format: [MISSING|SCOPE-CREEP|WRONG|CLAIM-FALSE] — spec line — problem.
```

### P2 — Standards + constraints

```
You are the STANDARDS reviewer, pass <N>, on the delta <range>.
Standards sources (read them): AGENTS.md (root), <module> AGENTS.md, DESIGN.md, docs/adr/
(touched areas), CONTEXT.md.
CONSTRAINT SOURCES the delta consumes — read every one the code references, even if outside
the delta: theme/token mappings, shared DTOs/enums, the ApiCallHandler contract, capability
codes, platform actuals, k6 conventions, error-message formats. Hold each doc sentence the
delta depends on against the code — a stale claim in a constraint source is a truth-class
finding (register), not a doc nit.
Smell baseline (judgement calls; repo standards override; skip what ktlint/detekt enforce):
Mysterious Name, Duplicated Code, Feature Envy, Data Clumps, Primitive Obsession, Repeated
Switches, Shotgun Surgery, Divergent Change, Speculative Generality, Message Chains, Middle
Man, Refused Bequest.
Report [HARD|SOFT|ESCALATE] file:line — problem — fix. Under 400 words.
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
- fallback paths that derive "existing" from a too-narrow key — the count-0 misfire class
  (register) has recurred; check every swallowed-write fallback against each constraint
  class that could have swallowed (PK, unique index, content, deleted-ness)
Report [HARD|SOFT|ESCALATE] file:line — problem — fix. Under 400 words.
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

**Register upkeep** — the resolution records new lesson-classes and occurrence bumps; the register above is the single source (handoffs link to it instead of restating registered classes).

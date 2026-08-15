# Code Review — Phased Loop (operational reference)

The loop contract lives in `AGENTS.md` ("Code review — phased loop"). This file is the
operational artifact: the phase prompt templates + flow-trace checklist + **lesson-class
register**, so every pass runs the same lenses (session 38's round-3 sanity pass was ad-hoc
and caught 7 issues the two-round structure missed — this doc makes that lens reproducible).

## Pass structure

A **pass** = P1–P4 as parallel sub-agents against the current delta. Fix → commit
(batch-fix commits) → next pass diffs `git diff <last-pass-commit>`. Every phase prompt
seeds the current accepted-SOFT list ("previously accepted — re-examine from your angle;
confirm, or re-rate upward if HARD-class from your lens") — the seeding is mandatory, the
load-bearing half of the two-sighting rule (Triage). Exit when one full pass reports
**zero HARD findings and no unadjudicated ESCALATEs** (triage empties the bucket
before exit). The exit pass then runs **P5 — architecture residue** (two parallel
sub-agents: architecture-depth + hygiene sweep; templates below) — its findings land in
the ARCH bucket (below), which never extends the loop; either P5 agent's in-ticket fixes
trigger the P5 loop-back (below).

**P5 loop-back** — P5's `fix-in-ticket` dispositions change code after the last standard
pass; they get the standard treatment like any fix batch. After P5's fixes commit: **one
standard P1–4 pass over `git diff <the P5 batch>`** — 0 HARD → exit; HARDs continue the loop
normally; no in-ticket fixes → no extra pass. Deliberately not per-pass: the residue lens is
empty on fix-sized deltas, and the gap is P5's *output*, not its timing (the #160 P5 batch
shipped a #141-class resurrect that the 4-lens review caught).

| Phase | Lens | Inputs |
|---|---|---|
| P1 | Spec conformance | delta vs ticket, line-by-line |
| P2 | Standards + constraints | delta vs standards AND constraint sources the code consumes |
| P3 | Behavior trace | composed tree, end-to-end flows incl. repeated attempts + back-stack |
| P4 | Adversarial edges | what breaks it: races, stale state, dead branches, unmapped slots |

## Classification

### Finding buckets

- **HARD** = bug / regression / security / data-loss / explicit documented-standard breach, or a lesson-class register match (below) — must fix, loop continues.
- **SOFT** = smell / judgement call → fix if cheap; else accept with a logged reason. Acceptance is provisional until the **two-sighting rule** is met (Triage): a SOFT survives to the exit pass only on two independent phase sightings; at exit, ≤3 two-sighted SOFTs may ride to P5/fog.
- **ESCALATE** = HARD-class flavor (regression / data / security) whose reachability the phase cannot fully prove. The phase reports it as ESCALATE and **triage adjudicates** — reachability doubt never downgrades a HARD-flavored finding to SOFT; it escalates.
- **ARCH** = architecture residue (P5, exit pass only): depth/locality findings — convoluted logic, dup unifiers, useless tests, shallow abstractions (architecture-depth agent) — plus hygiene findings — grep-proven dead code, layering crossings (hygiene-sweep agent). Never blocks exit, never counts toward the SOFT budget; ≤4 per ticket (architecture-depth) / ≤3 (hygiene sweep), merged by triage. Fix if cheap in-ticket; else record in the resolution comment, from where it graduates into the map's fog lines (the wayfinder graduation pipeline). A finding that matches a registered lesson-class is HARD, not ARCH.

### Lesson-class register

The register is the memory of the loop: documented bug classes that recurred as HARD once and must default HARD thereafter. **A finding matching a registered class is HARD unless the phase (or triage) proves it inert** — the proof burden sits on the acceptor, not the reporter (the rejected-HARDs discipline, reversed). Resolutions append new classes and bump occurrence counts; builds apply the register as much as audits.

| Class | Signature | Origin (occurrences) |
|---|---|---|
| count-0 misfire | a swallowed-write fallback (`insertIgnore` count 0) that returns/derives "existing" by a key too narrow for the constraint class that swallowed (PK vs unique-index vs content) — wrong data served to the caller | #137 r1 HARD; #146 HARD-class (2); #149 (3 — the version-lock race: the service's separate-transaction pre-check + an unconditional read-back of the row the atomic UPDATE failed to win served the OTHER writer's row as the caller's own 200 — fixed with an affected-row count → 409; repo throws `VersionMismatchException` (ADR-0016), never the bare `ConflictException`) |
| truth-class | a claim or doc line contradicted by shipped code — resolution comments, ADR/AGENTS.md/KDoc | #145 (2 HARD); #146 (1 HARD-class); #148 (ticket+KDoc claimed "serialName + optional `?arg={arg}` — verified"; `RouteBuilder` emits non-optional args as `/` path segments) |
| lazy lock | Exposed `forUpdate()` (or any deferred op) without a terminal op silently no-ops — and the docs may stale-claim it unavailable | #136 r2; #146 doc line (2) |
| exact-path gate | a route-level gate written for a path shape the actual route never matches (segment-count drift) — a gate that never fires | #114; #128/#131 (5+); #148 (nav pattern strip written for query-only shapes; non-optional args are `/` path segments) |
| layout starvation | a `fillMaxSize`/`fillMaxHeight`/intrinsic-measure misuse that gives a pinned sibling (button, list, divider) zero height inside a wrap-content parent — UI silently invisible | #144 D5; #147 pass-2 divider (2) |
| fix-that-didn't-land | a claimed fix that never reached the file — imports/params added but the body replacement silently missed (string-mismatch edits); the commit message and the phases disagree — verified by reading the file, never the commit | #147 pass-4 (1) |
| keyed-mirror ordering | a stale same-key response becoming the LAST WRITER on a per-key keep-last mirror — ordering a key alone cannot see (two loads of the same key in flight: the older snapshot commits after the newer). Fix shape: newest-launch-wins stamp gating the commit, or a per-key in-flight guard when a skipped refetch is safe. | #162 pass-1 (2 phases independently) |

### Triage (driving agent, after each pass's phases report)

Triage re-derives **every** finding's class from the phase's own evidence — phase ratings are inputs, never authority (pass-1 phases rated both #146 HARD-class findings SOFT; the synthesis caught them). Done when:

- every finding re-classified from evidence, register matches checked, ESCALATE entries adjudicated (HARD → fix, or rejected with proof of inertness);
- the driving agent itself hunts the register classes in the constraint sources — reads the AGENTS.md/ADR/KDoc lines the delta depends on (the #146 doc contradiction was caught this way, not by a phase);
- every accepted SOFT is sighted twice (**the two-sighting rule**): the accepting lens plus an independent confirmation — a later phase's re-examination from its own angle, possibly across passes. The mandatory prompt seeding supplies it: every phase prompt lists the accepted SOFTs and instructs "re-examine from your angle; confirm, or re-rate upward if HARD-class from your lens" — each phase addresses every listed SOFT explicitly. The confirming lens must cover the finding's class (triage assigns it at acceptance; P5's architecture re-rate confirms architecture-flavored SOFTs only — never the correctness/behavior/standards classes); triage's own re-derivation confirms the disposition, never the SOFT class. Acceptance is never load-bearing (round-1 SOFTs became round-3 HARDs — the handoff caught them; the rule makes the handoff unskippable).
- the exit pass cannot accept a one-sighting SOFT: it is fixed in-ticket or deferred to a seeded confirmation pass (usually empty-delta — the phases re-examine the SOFTs and re-derive the ticket's flows); at exit, ≤3 two-sighted SOFTs may ride to P5/fog, each with a logged reason.

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
finding (register), not a doc nit. Also hold the COMPOSED SEAM MAP: every file the delta
touches must sit in its documented layer (docs/architecture.md module boundaries,
composeApp/backend/shared seams, package conventions) — a delta file crossing a documented
boundary is HARD (documented-standard breach); an undocumented seam smell is SOFT.
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
  defaults that hide future enum values — grep-prove it: the delta's new public API must
  have callers outside the delta (zero callers + not a declared seam member = dead)
- unmapped slots (theme tokens the code references but the theme doesn't define — check the
  theme file directly)
- string/format coupling (substring matches on producer formats)
- fallback paths that derive "existing" from a too-narrow key — the count-0 misfire class
  (register) has recurred; check every swallowed-write fallback against each constraint
  class that could have swallowed (PK, unique index, content, deleted-ness)
Report [HARD|SOFT|ESCALATE] file:line — problem — fix. Under 400 words.
```

### P5 — Architecture residue (exit pass)

Runs once, after triage reports 0 HARD on the exit pass. **Two parallel sub-agents**:
**P5a — architecture-depth** (below) and **P5b — hygiene sweep** (below), both seeded with
the ticket's two-sighted SOFTs (all passes). Classifies into the ARCH bucket (above); a
finding matching a registered lesson-class is HARD, not ARCH — report it in the HARD format
so triage treats it as a loop continuation. Each finding gets a disposition:
fix-cheap-in-ticket (recommend) or graduation-material (the fix is cross-ticket; name the
fog line it should graduate into).

**P5a — Architecture-depth**

```
You are the ARCHITECTURE reviewer on the FULL ticket delta `git diff <pre-ticket-commit>` —
the whole ticket's shipped code, not the exit pass's last batch-fix delta (fixes are too
small to carry architecture residue; the residue lives across the ticket). This is the
exit pass — the loop found 0 HARD; your job is residue, not blocking findings.
Repo: /mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app.
Vocabulary: use the /codebase-design terms exactly — module, interface, depth, seam, adapter,
locality, leverage, the deletion test, the two-adapters rule (one adapter = hypothetical seam,
two = real), the interface-is-the-test-surface principle.

Inputs: (a) the ticket's two-sighted SOFTs (<list them>); (b) the full ticket delta + composed tree.

For each accepted SOFT: re-rate from the architecture angle — depth/locality smell that should
graduate into the map's fog lines, or a deliberate cost that stays buried? A deliberate
duplication with a recorded reason (e.g. "presentational params mirror the VM functions") is
the latter — confirm the reason once, do not re-raise.

Then sweep the delta + composed tree through four lenses, one section each:
1. Convoluted logic — locality: decision logic repeated at call sites that should sit behind
   one interface; conditionals hiding a state machine. Apply the deletion test: would the fix
   concentrate complexity or just move it?
2. Dup unifier — the two-adapters rule: the same shape at 2+ sites (platform actuals, VM
   patterns, error surfaces, DTO mapping) is a real seam; name the extraction candidate and
   the interface it would present.
3. Useless-test pruning — the interface is the test surface: tests that pass without the
   behavior (vacuous asserts, implementation echoes, tests that never fail). Name the test
   and the behavior it fails to pin.
4. Abstraction improver — depth: interface ≈ implementation (shallow), one-adapter
   hypothetical seams, seams in the wrong place.

Report at most 4 findings total: [ARCH] file:line — problem — fix-shape — disposition
(fix-in-ticket | graduate: <fog-line name>). One section per lens, empty sections say so.
In-ticket fixes go through the P5 loop-back (one standard P1–4 pass over the P5 batch)
before exit.
Under 350 words.
```

**P5b — Hygiene sweep** — mechanical, grep-driven; proof over judgement. Every finding
carries its grep/search evidence; judgement findings (seams, depth, extraction candidates)
belong to the architecture-depth agent.

```
You are the HYGIENE reviewer on the FULL ticket delta `git diff <pre-ticket-commit>` + the
composed tree — the whole ticket's shipped code, not the exit pass's last batch-fix delta.
This is the exit pass — the loop found 0 HARD; your job is residue, not blocking findings.
Repo: /mnt/windows10/BACKUP/Jayson/home/Workspace/IdeaProjects/company-app.

Three mechanical sweeps, each finding quoted with its grep evidence:

1. Dead code the ticket ADDED or EXPOSED: every new public symbol (class, function, flow,
   DTO field) — zero callers outside the delta and not a declared seam member (expect/actual
   pair, route registration, slot contract) = dead. Also: unused imports/params the delta
   touched; non-exhaustive `when`s over enums the delta extended.
2. Layering: every file the delta touches sits in its documented layer (docs/architecture.md
   module boundaries, composeApp/backend/shared seams, package conventions). A crossing of a
   DOCUMENTED boundary is HARD — report in the HARD format so triage treats it as a loop
   continuation; an undocumented seam smell is ARCH.
3. Unused public surface the delta left behind: public API whose consumer died in the delta
   (a flow no screen collects, a function no caller reaches).

Report at most 3 findings total: [HARD|ARCH] file:line — problem — fix — disposition
(fix-in-ticket | graduate: <fog-line name>). In-ticket fixes go through the P5 loop-back.
Under 250 words.
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
Pass 3 (delta <sha>): 0 HARD across all four phases — exit. P5 architecture residue: 2 ARCH
(1 fixed in-ticket, 1 graduated: <fog-line name>). Accepted SOFTs: <list with reasons>.
```

ARCH graduates land in the handoff's standing-fog section, from where the wayfinder map's
"Not yet specified" picks them up (the existing graduation pipeline).

**Register upkeep** — the resolution records new lesson-classes and occurrence bumps; the register above is the single source (handoffs link to it instead of restating registered classes).

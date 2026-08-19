# Decision loop — deferred human review

## No-question policy

The agent never asks the user a question or invokes the question tool. A design that
needs human input becomes a separate tracker issue labeled `needs-info` or
`ready-for-human`, with verified facts, the exact decision, and its blocking impact.
The agent continues independent AFK work; if none remains, it records the blocker and
stops. Human review happens asynchronously on that issue.

Every deferred human-review ticket on this map runs the decision-loop discipline before
its design reaches the human. It mirrors the code-review phased loop
(`docs/agents/code-review-loop.md`) — parallel sub-agents per phase, HARD/SOFT triage,
batch-fix commits, exit on one full pass with zero HARD — but the artifact under review
is a **design**, not a diff.

## Standing frame (applies to every lens, every pass)

- **We are still in development.** There are no production users. Migration cost is ZERO — never weigh it.
- **Always choose the best long-term option.** Not the cheapest, not the smallest diff, not the least surprising. The option that is most correct for two years from now.
- **Falsify every claim.** Every premise, every recommendation, every "this gate already exists" gets an adversarial attempt at proof-wrong before it enters a question.
- **Facts are the agent's job, never the human's.** Any premise that needs verification is verified in code/migrations/docs BEFORE it is presented as a choice. A choice built on an unverified claim is a false-premise defect — the decision loop's cardinal sin (session-56 Q3: a BRANCH `EDIT_BRANCH_DATA` inviter gate was presented and only fact-checking against V2/V16 revealed it was a vacuous gate with zero eligible holders).
- **Present to the human in simple language.** Short sentences. No jargon where a plain word works. The gist of the question must be graspable at a glance. Technical accuracy is NOT sacrificed for simplicity — the two coexist.
- The human decides asynchronously on the tracker issue. The loop sharpens and falsifies;
  it never guesses or closes the decision on the human's behalf.

## The lenses

One lens per phase, each run as a parallel sub-agent per pass:

**L1 — Fact integrity.** Every factual claim in the design and its presentation is verified against the codebase: file paths, line numbers, table columns, gate behavior, existing endpoints, enum values, migration versions. Claims that fail verification are HARD. Catches the false-premise class (session-56 Q3's vacuous gate; session-52's discovery that BRANCH_DAY grants were written but never enforced).

**L2 — Domain coherence.** The design must not contradict CONTEXT.md vocabulary, ADRs, settled decisions, business rules, or previously locked designs. Term misuse, invented terms where canonical ones exist, and decisions that silently re-litigate a settled question are HARD. Also checks the design against the constraints it consumes (capability model, day-state rules, DTO shapes, enforcement surfaces).

**L3 — Long-term architecture.** With the dev-stage frame: is this the best shape for two years from now? Coupling, extensibility, future-feature room (the design must not paint the domain into a corner — e.g. a day-scoped relief model must not preclude multi-day invites later), duplication of concept (two structures for one concept — the coordination-cost class), and whether the design sets up the next decisions cleanly. Pure judgement calls are SOFT; shapes that close doors the domain will plausibly want are HARD.

**L4 — Adversarial falsification.** Try to kill the design. Edge cases, races, repeated attempts, competing designs, dead gates (a gate no legitimate caller can pass, or one that doesn't actually fire), abuse surfaces (who can do this and should they), expiry/state-transition holes, and "this can't happen" shapes that the domain actually permits. Each attempt records pass/fail. A design that survives the strongest attempts is stronger; a design that dies gets revised, not defended.

**L5 — Comprehension.** Reviews the presentation TO the human, not the design itself. Can the gist be grasped at a glance? Short sentences? No jargon standing between the question and the reader? Are the choices real (all options actually possible, none built on a false premise)? Is the recommendation stated plainly and the falsification record readable? Anything that would make the human re-read twice or misread a choice is HARD for this lens (the human's confusion is the failure signal). The style target is simple language — short sentences, plain words, gist at a glance — NOT strict ASD-STE100 compliance (the user's reference frame, not a rulebook): technical accuracy wins when the two conflict.

## Loop mechanics

- **Pass = all five lenses in parallel** on the current design state (the first pass reviews the raw design tree; later passes review the delta since the last revision).
- **Triage**: HARD (false premise / domain contradiction / dead gate / architectural dead-end / comprehension failure) → design revised, loop continues. SOFT (smell / judgement call) → fix if cheap, else accept with a logged reason under the **two-sighting rule** (mechanics in `docs/agents/code-review-loop.md` — same rule as the code-review loop): an accepted SOFT rides to the resolution only on two independent lens sightings (the accepting lens + a confirming lens covering the finding's class; triage re-derivation and any architecture-only re-rate confirm the disposition, never the SOFT class); the accepted list seeds every subsequent lens prompt (mandatory); the exit pass cannot accept a one-sighting SOFT (fix it, or run a seeded confirmation pass); ≤3 two-sighted SOFTs may ride at exit, each with a logged reason.
- **Exit**: one full pass with zero HARD findings across all five lenses. The design is then presented to the human for the final confirmation.
- The falsification record + the locked design + the simple-language presentation become the ticket resolution.

## AFK Candidate Verification

Architecture audits that produce multiple accepted candidates must explore every candidate before ranking implementation order. A higher-ranked candidate never suppresses another candidate's dossier.

GPT-5.6 Luna is the sole verifier for this repository's AFK architecture flow. Use continuous scoring when OpenCode2 exposes scoring-token logprobs. Otherwise use structured repeated rubric scoring and record `structured` mode with reduced confidence. Alternate candidate positions and blind candidate labels to reduce presentation bias. Repeat evaluation across L1 fact integrity, L2 domain coherence, L3 long-term architecture, L4 adversarial falsification, and L5 comprehension. Feasibility is optional additional evidence.

Verifier output is advisory. Deterministic repository evidence is authoritative: paths, symbols, requirements, ADR status, schema, grep results, builds, and tests. A candidate cannot reach `verified` while a hard deterministic claim fails.

Each candidate lifecycle is:

`identified → evidenced → explored → falsified → verified → dispositioned → ticketed → implemented → re-audited`

Every transition records its artifact and completion evidence in the audit ledger. `ticketed` is allowed only after all in-scope candidates are `dispositioned`. AFK flow selects implementation order autonomously; human input is reserved for business ambiguity, safety, external authorization, irreversible scope, or a full audit with no justifiable candidate.

Verifier packet must record mode, model, blind position, L1 fact integrity, L2 domain coherence, L3 long-term architecture, L4 adversarial falsification, L5 comprehension, deterministic-gate result, HARD/SOFT triage, confidence, and artifact pointer. Feasibility is additional evidence, never a replacement for L5. A candidate cannot reach `verified` or `ticketed` with a missing packet field, failed deterministic evidence, or untriaged HARD finding.

## Where it lives

- This doc is the authority on the decision loop.
- The wayfinder "Work through the map" flow invokes it for every grilling ticket.
- Root AGENTS.md carries a pointer so any agent session loading the repo sees the frame.

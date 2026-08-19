Audit this entire codebase for materially useful simplifications in its data structures, state representation, control flow, algorithms, and ownership.

This is an audit-only exercise. Do not edit files, run tests, implement recommendations, commit, or push. Read-only inspection commands are allowed.

You are the coordinator. Continue until the complete codebase has been reviewed and the final audit is validated.

1. Establish the coverage contract

Inspect the repository and inventory every identifiable subsystem.

Give each subsystem:

- a stable ID and descriptive name;
- an exact ownership boundary;
- its key implementation files;
- relevant public interfaces, major call sites, and tests;
- a status: queued, in review, recommend, or skip.

Include frontend, backend, shared infrastructure, platform bridges, generated-contract ownership, and test/tooling infrastructure where materially relevant.

Create one canonical scratchpad or report containing:

- the subsystem inventory;
- confirmed opportunities;
- explicit skip decisions;
- cross-cutting patterns;
- duplicates and superseded findings;
- final priorities and dependencies;
- an audit log.

Treat this inventory as the coverage contract. Do not assume broad catch-all rows prove coverage.

2. Run bounded subsystem reviews

Use fresh, read-only agents where available. Give every worker one distinct subsystem with an exact, non-overlapping ownership boundary.

Keep concurrency bounded to the number of lanes you can actively coordinate. Use one consolidated wait mechanism, do not interrupt productive workers merely because they are slow, and close completed workers after harvesting their results.

Each worker receives this brief:

Review the assigned subsystem for at most two materially useful simplifications in its data structures, state representation, or organizing model.

Inspect its implementation, public interfaces, major call sites, and existing tests. Stay within the assigned ownership boundary. You may identify cross-subsystem concerns, but do not expand the scope to solve them.

Look for:

- scattered booleans or nullable fields that permit invalid combinations and should become a state machine or discriminated union;
- repeated assumptions about object shape that need a shared typed model;
- duplicated branching that a small map, registry, reducer, or command model would remove;
- unclear state or behavior ownership that a small module boundary would clarify;
- repeated scans, transformations, or lookups where a more appropriate collection or index would materially simplify behavior;
- lifecycle, concurrency, or async states whose representation permits stale or contradictory state.

Do not force an abstraction. Prefer boring local code when it is already clear.

Do not recommend changes solely for stylistic consistency, hypothetical extensibility, minor line-count reduction, or moving existing branching behind a new type.

Return at most two opportunities per bounded subsystem. Preserve every materially accepted opportunity in the candidate ledger; do not select one winner and discard the rest. If nothing clearly meets the threshold, return `skip`.

For every recommendation, provide:

1. Verdict: recommend or skip.
2. Evidence with exact file and line references.
3. Current complexity or invalid states.
4. Proposed representation and why it is simpler.
5. Smallest credible implementation scope, including affected files and interfaces.
6. Regression risks and migration concerns.
7. Existing and additional validation required.
8. Confidence: high, medium, or low.

For every accepted candidate, complete this lifecycle before creating implementation tickets:

`identified → evidenced → explored → falsified → verified → dispositioned → ticketed → implemented → re-audited`

GPT-5.6 Luna is the sole AFK verifier. Use continuous scoring when OpenCode2 exposes scoring-token logprobs; otherwise use structured repeated rubric scoring and record reduced confidence. Alternate candidate positions and blind labels. Deterministic repository evidence is authoritative and blocks verification when it fails. Every candidate must receive a dossier, deterministic fact pass, verifier pass, adversarial pass, and explicit `implement`, `defer`, `reject`, or `duplicate` disposition.

The verifier pass is not complete until the candidate dossier contains this packet:

```text
candidate: <stable ID>
mode: continuous | structured
model: GPT-5.6 Luna
position: <blind position or label>
L1 fact integrity: <pass/findings>
L2 domain coherence: <pass/findings>
L3 long-term architecture: <pass/findings>
L4 adversarial falsification: <pass/findings>
L5 comprehension: <pass/findings>
deterministic gate: <pass/failing evidence>
HARD findings: <zero or linked findings>
SOFT findings: <zero or two-sighted accepted findings with reason>
confidence: high | reduced
artifact: <ledger section, issue, or session pointer>
```

`feasibility` may be recorded as an additional criterion, but it does not replace L5 comprehension. A candidate cannot reach `verified` or be ticketed while any packet field is missing, deterministic evidence fails, or an untriaged HARD finding remains.

3. Validate and synthesize

The coordinator must independently verify every finding against the current repository before accepting it.

Reject, narrow, or demote recommendations that are vague, duplicate another finding, misunderstand intentional semantics, or merely relocate complexity.

Record skips as completed coverage. Deduplicate overlapping findings and assign each accepted recommendation to one authoritative subsystem.

Continue opening bounded review batches until every inventory row is complete.

4. Audit the audit

Before finishing, run fresh independent passes for:

- repository coverage and missing subsystem boundaries;
- duplication and ownership overlap;
- materiality and over-abstraction;
- schema completeness;
- dependency-aware priority ranking.

If the coverage pass finds a real omission, add an explicit subsystem row and audit it. Do not hide it by broadening a previously completed boundary.

Rank the final recommendations by concrete impact, confidence, implementation effort, blast radius, and prerequisites. Identify the best first implementation slices.

The audit is complete only when:

- every identifiable subsystem has been reviewed;
- every subsystem has a recommendation or explicit skip;
- every finding has complete evidence, scope, risk, and validation fields;
- duplicates and weak abstractions have been removed;
- priorities and dependencies are internally consistent;
- the repository remains unchanged.

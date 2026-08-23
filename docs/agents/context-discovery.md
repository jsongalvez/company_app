# Context discovery

How to build a working set for a task: **search broadly; read narrowly.** Repository
context is a graph to traverse, not a corpus to preload. Search results, imports,
filenames, call sites, and test names may be used liberally to find candidates — but
opening a file must be justified by one concrete unresolved question. "Background,"
"understanding the area," and "might be related" are not justifications.

Canonical here. Root `AGENTS.md`, `backend/AGENTS.md`, `docs/deep-modules.md` (the
module router), and the Wayfinder lifecycle point at this file rather than restating it.

## Protocol

### 1. Resolve the owning module first

From the issue, changed paths, domain terms (`CONTEXT.md`), named symbols, and specs,
identify the **smallest semantic module that owns the requested behavior** — use the
module router, `docs/deep-modules.md`. If several participate, start with the owner;
treat the others as dependencies. `backend/` as a whole is not a context unit.

### 2. Seed a minimum working set

Only:

- issue body + authoritative comments (GitHub outranks stale handoff text);
- required root/module agent instructions;
- the owning module's card in `docs/deep-modules.md`;
- the card's anchor implementation(s);
- directly relevant behavioral test/contract where already known.

Do not preload neighboring module implementations.

### 3. Discover before reading

Use cheap discovery first: symbol/reference search, imports, call sites, filenames,
test names, package paths, targeted `rg`. Search results are **candidate edges**, not
read permissions.

### 4. Cross module boundaries through public seams

When your module depends on module B, inspect only B's declared **Public seam** from
its card. Do not descend into B's repositories, helpers, routes, tests, schema, or
consumers unless one holds:

1. the change modifies B's seam or contract;
2. an unresolved correctness question depends on B's implementation;
3. compiler/test/runtime evidence forces expansion;
4. an applicable invariant cannot be verified from the seam + authoritative docs alone.

Example: `SessionService -> BranchDayService` permits reading the relevant
`BranchDayService` surface. It does not justify loading Branch Day repository
internals, routes, tests, Capability internals, schema, or other consumers.

### 5. Expand one evidence-backed hop at a time

Keep a prompt-level frontier (no tracked file unless persistence clearly pays):

- **Target** — behavior being changed.
- **Loaded** — file/symbol, and why it is required.
- **Unresolved** — concrete questions blocking a correct implementation.
- **Candidates** — discovered-but-unopened files/symbols, each with the exact
  condition that would justify opening it.

Before any substantial expansion, name the unresolved question the new file answers.
If none exists, do not open it.

### 6. Stop condition

Discovery is finished when:

- the owning module is identified;
- the affected entrypoint → behavior → persistence/output path is understood;
- directly affected cross-module seams are known;
- applicable requirements/invariants (ADRs, backend rules) are known;
- relevant test evidence is identified;
- no unresolved correctness question requires unseen implementation.

Then implement. **More repository understanding is not itself a reason to keep
reading.** Recursive familiarization is out of scope for normal startup. Discovery
reopens only when new evidence creates a concrete unresolved question: symbol
dependency, compiler error, failing test, behavior trace, architecture rule, schema
constraint, unexpected runtime behavior.

## Retrieval rules by material type

| Material | Open when | How |
|---|---|---|
| Tests | they define/validate the behavior being changed | search test names for the symbol under change; open the hit, not the directory |
| Schema/migrations (`backend/src/main/resources/db/migration/`) | change affects persisted shape, constraints, locking, transactions, defaults, views, DB behavior | search migration files for table/constraint names; load only matching versions — `V1__full_schema.sql` is the authoritative current shape |
| ADRs (`docs/adr/`) | the touched seam/decision has architectural authority | scan ADR titles/status fields; read only decisions in the area |
| Business rules/specs | behavior or domain semantics at issue | `docs/business-requirements.md`, `docs/specs/`; search terms, don't read whole files |

Never read an entire directory because one file inside may matter. Derive caller/
consumer/test lists with code search on demand — never maintain them in docs.

## Context-budget invariant

Progressive disclosure is mandatory. If working context grows large, stop broadening:
summarize the known module graph and continue from pointers; prefer re-searching or
re-opening a specific file later over retaining broad unrelated source. Uncontrolled
context accumulation is a failure mode, distinct from legitimately deep work on a hard
ticket. This refines the existing rule that handoffs happen "before context becomes
crowded" — same discipline, applied during discovery instead of only at handoff.

## Wayfinder compatibility

Fresh starts and resumptions rebuild the working set from: ticket/GitHub authority →
owning module card → anchors → code search. Handoffs stay compact pointer packets:
never include source summaries, dependency trees, issue bodies, or file lists — a
successor reconstructs context cheaply via this protocol instead of inheriting it.

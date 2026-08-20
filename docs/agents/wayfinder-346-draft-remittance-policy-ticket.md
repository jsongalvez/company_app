# Map #180 Candidate: Allow Overlapping Draft Remittances

## Question

How should implementation align draft remittance uniqueness with approved
business policy?

## Approved policy

Same-Branch, same-type, same-date DRAFT remittances may coexist. Submitted
remittance overlap remains constrained.

## Scope

- Add Flyway migration narrowing the status-blind remittance uniqueness rule to
  submitted rows, preserving submitted overlap protection.
- Make distinct-UUID draft creation collisions deterministic under the migrated
  schema while preserving same-UUID, same-Branch idempotent retries and
  cross-Branch collision rejection.
- Add focused migration, repository, service, and API regression coverage.

## Verifier packet

- mode: structured
- model: GPT-5.6 Luna
- blind position: ALPHA
- operational impact: Coordinators can prepare overlapping drafts without false
  creation failures; list/detail UX must distinguish drafts and submission
  conflicts remain recoverable.
- L1 fact integrity: pass; schema, service, repository, and requirements agree
- L2 domain coherence: pass; Draft and Submitted policies are separated
- L3 long-term architecture: pass; migration and repository own persistence policy
- L4 adversarial falsification: pass; prior status-blind collision and distinct-ID path reproduced
- L5 comprehension: pass
- deterministic gate: pass; approved policy recorded in #286 and requirements/docs
- HARD findings: zero after migration and deterministic collision handling
- SOFT findings: one; draft-list duplicate identity needs focused UI consideration
- confidence: high
- artifact: issues #286/#247 and `docs/agents/wayfinder-343-handoff.md`

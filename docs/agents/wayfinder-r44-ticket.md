# Build: make active assignment creation conflict-safe

Part of Map #180.

## Question

Can active user-to-Branch assignment creation classify concurrent business-key conflicts deterministically while preserving idempotent retries and audit atomicity?

## Evidence

`UserBranchAssignmentService.create` pre-checks `(userId, branchId)` outside the repository transaction. `UserBranchAssignmentRepository.create` uses `insertIgnore` and returns only `insertedCount`; a distinct UUID that loses the active unique index is followed by a lookup of a row that cannot exist for that UUID. V1 owns the active unique index `idx_one_active_assignment`.

## Scope

Make repository-owned creation distinguish same-ID idempotency from a different-ID active assignment collision. Keep service pre-check and post-create lookup for established sequential duplicate and same-ID retry behavior; classify only the race inside the repository. Preserve audit-once semantics, authorization, and schema. Add focused sequential, same-ID, distinct-ID concurrent, and audit regression coverage. No schema change.

## Verification packet

- mode: structured
- model: GPT-5.6 Luna
- blind position: BETA
- L1 fact integrity: pass
- L2 domain coherence: pass with HARD business-key ownership breach
- L3 long-term architecture: pass with HARD transaction-ownership breach
- L4 adversarial falsification: pass, distinct-ID race follows swallowed active-index conflict
- L5 comprehension: pass
- deterministic gate: pass, current service/repository/schema/test paths and unique index agree
- HARD findings: zero after repository-owned conflict classification
- SOFT findings: zero after same-ID, sequential-duplicate, service-race, repository-race, and persisted audit-row coverage
- confidence: high
- artifact: `docs/agents/architecture-audit-180.md`, Permanent-Map Refresh - Session 287

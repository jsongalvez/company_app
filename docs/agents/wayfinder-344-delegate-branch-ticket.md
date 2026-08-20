# Map #180 Candidate: Restrict Delegate Assignment to Medical Mission Branches

## Question

Should medical mission delegate assignment reject ordinary clinic and
provincial Branch values before writing `EDIT_BRANCH_DATA` capability grants?

## Scope

Validate Branch existence and `MEDICAL_MISSION` type in the service-owned
assignment path. Preserve global `ASSIGN_DELEGATE` authorization, audit, and
valid mission assignment behavior. Add ordinary/provincial rejection tests.

## Evidence

- `MedicalMissionDelegateService.kt:17-43` passes `branchId` without Branch type validation.
- `MedicalMissionDelegateRepository.kt:47-54` grants branch edit capability for any ID.
- `docs/business-requirements.md:377-380` and `docs/engines.md:97-101` limit delegation to
  medical mission Branch values.

## Verifier packet

- mode: structured
- model: GPT-5.6 Luna
- blind position: GAMMA
- L1 fact integrity: pass
- L2 domain coherence: pass with HARD capability-scope breach
- L3 long-term architecture: pass; service owns domain validation
- L4 adversarial falsification: pass; arbitrary Branch ID grants edit access
- L5 comprehension: pass
- deterministic gate: fail; service/repository accept ordinary Branch values
- HARD findings: zero after Branch type validation
- SOFT findings: zero
- confidence: high
- artifact: Session 344 backend audit; `MedicalMissionDelegateService.kt:17-43`

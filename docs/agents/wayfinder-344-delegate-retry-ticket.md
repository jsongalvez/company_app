# Map #180 Candidate: Keep Revoked Delegate Retries Idempotent

## Question

Should retrying an already revoked medical mission delegate UUID return the
existing ended delegate without creating a fresh active capability?

## Scope

Make delegate assignment and capability insertion one repository-owned
idempotent operation. Preserve same-ID retries, revocation, audit atomicity, and
distinct-ID behavior. Add assign, revoke, retry, duplicate-grant tests.

## Evidence

- `backend/src/main/kotlin/com/companyb/companyapp/repository/MedicalMissionDelegateRepository.kt:40-55`
  ignores duplicate delegate IDs but always inserts a capability.
- After revoke, retrying the same UUID can create an active grant for an ended
  delegate; repeated retries can accumulate grants.
- Existing coverage at `MedicalMissionDelegateServicePostgresTest.kt:81-93`
  does not cover post-revoke retry.

## Verifier packet

- mode: structured
- model: GPT-5.6 Luna
- blind position: BETA
- L1 fact integrity: pass
- L2 domain coherence: pass with HARD idempotency/revocation breach
- L3 long-term architecture: pass; repository owns atomic insert side effects
- L4 adversarial falsification: pass; assign -> revoke -> same UUID retry re-grants
- L5 comprehension: pass
- deterministic gate: fail; unconditional capability insert follows `insertIgnore`
- HARD findings: zero after inserted-count/idempotency guard
- SOFT findings: zero
- confidence: high
- artifact: Session 344 backend audit; `MedicalMissionDelegateRepository.kt:40-55`

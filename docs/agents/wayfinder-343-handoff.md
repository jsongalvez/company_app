# Handoff - Map #180, Draft Remittance Uniqueness, Session 343

## Authority

- Map #180 was workflow authority. `docs/agents/wayfinder-342-handoff.md` was
  state evidence only.
- Loaded `/wayfinder`, `CONTEXT.md`, architecture, business requirements,
  engines, issue-tracker guidance, and backend/Compose/shared module guidance.
- REST API was used for Map #180 because `gh issue view` failed on deprecated
  GitHub Projects GraphQL fields.

## Claim

- Rechecked Map #180 frontier: #247 was first open, unblocked, and unassigned;
  #267 was second.
- Claimed #247, `Decision: define draft remittance uniqueness policy`, by
  assigning `jsongalvez` before investigation.
- Native parent link verified: #247 parent is Map #180.

## Resolution State

- #247 cannot be resolved safely without business authorization.
- Verified facts:
  - `backend/src/main/resources/db/migration/V1__full_schema.sql:429-453`
    applies `UNIQUE (branch_id, type, submitted_date)` to all remittances,
    including DRAFT rows.
  - The same migration separately scopes `no_remittance_overlap` to
    `status = 'SUBMITTED'`.
  - `RemittanceService.createDraft` uses today's `submittedDate`.
  - `RemittanceRepository.createDraft` uses `insertIgnore`, so a distinct UUID
    colliding with status-blind uniqueness cannot be safely classified as an
    idempotent retry.
  - Business and architecture docs state DRAFT remittances may overlap.
- Created `needs-info` issue #286, `Decision needed: allow overlapping draft
  remittances`, with verified facts, exact decision, blocker, and smallest safe
  next action.
- Linked #286 from #247. #247 remains claimed and open pending policy.
- Correction comment was posted after an initial shell-quoting error; corrected
  comment contains intact code identifiers.

## Verification

- #247: open, assigned `jsongalvez`, label `ready-for-agent`, native parent #180.
- #286: open, unassigned, label `needs-info`.
- `git diff --check`: PASS.
- No production files changed. No ADR needed. No commit or push made because
  implementation is blocked by unresolved financial policy.

## Next Action

- After #286 records policy, continue #247 only if implementation is authorized.
- If drafts may coexist, create one native implementation child under Map #180
  for migration, collision classification, and focused tests; verify its parent
  link, then claim only that child in a later session.
- If draft uniqueness is intentional, record that decision and create the
  smallest deterministic conflict-handling/test child.

**Status:** #247 blocked by #286; handoff complete.

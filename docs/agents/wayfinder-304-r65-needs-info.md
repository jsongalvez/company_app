## Verified facts

- `backend/src/main/resources/db/migration/V1__full_schema.sql:437-453` defines
  status-blind uniqueness on `(branch_id, type, submitted_date)`.
- `backend/src/main/kotlin/com/companyb/companyapp/service/finance/remittance/RemittanceService.kt:90-102`
  creates drafts with today's `submittedDate`.
- `backend/src/main/kotlin/com/companyb/companyapp/service/finance/remittance/RemittanceRepository.kt:262-274`
  uses idempotent insertion and cannot read back a distinct UUID that loses this
  uniqueness collision, leading to generic failure.
- `docs/business-requirements.md:343-345` and `docs/architecture.md:528` state
  that draft remittances may overlap; submitted remittance overlap remains
  constrained.

## Exact decision

Should same-branch, same-type, same-date DRAFT remittances be allowed to coexist?

## Blocker

Implementation must not guess whether to alter the committed schema constraint.
The answer changes financial uniqueness and migration behavior.

## Smallest safe next action

Record policy. Then create one implementation child to align schema, repository
conflict handling, tests, and submitted-remittance overlap behavior.

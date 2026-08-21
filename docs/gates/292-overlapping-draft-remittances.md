# Gates - 292: overlapping draft remittances

- [x] G1: Draft remittance uniqueness is status-scoped in a forward migration
  CHECK: grep -q "DROP CONSTRAINT IF EXISTS remittance_branch_id_type_submitted_date_key" backend/src/main/resources/db/migration/V23__allow_overlapping_draft_remittances.sql && grep -q "WHERE status = 'SUBMITTED'" backend/src/main/resources/db/migration/V23__allow_overlapping_draft_remittances.sql
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Focused remittance ownership tests cover overlapping drafts and submitted overlap
  CHECK: ./gradlew :backend:test --tests com.companyb.companyapp.service.RemittanceDraftOwnershipPostgresTest
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

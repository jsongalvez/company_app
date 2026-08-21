-- #121: audit log branch scoping foundation.
-- audit_log was global by schema; reads cannot be scoped to a caller's branch window
-- without a branch column (per #104 D6). Populated at write time going forward
-- (AuditLogRepository.record* branchId param). No backfill: no production users yet
-- (the upcoming migration-merge ticket consolidates V migrations anyway). Branchless
-- tables (app_user/client/branch/product/product_category/concern) and legacy rows
-- stay NULL -> Owner/Accountant-only visibility per #104 D6.

ALTER TABLE audit_log ADD COLUMN branch_id UUID REFERENCES branch(id);

CREATE INDEX idx_audit_branch ON audit_log (branch_id) WHERE branch_id IS NOT NULL;

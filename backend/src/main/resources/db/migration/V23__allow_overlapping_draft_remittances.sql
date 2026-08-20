-- Drafts are collaborative working records and may overlap. Submitted rows
-- retain one remittance per branch, type, and submission date.
ALTER TABLE remittance
    DROP CONSTRAINT IF EXISTS remittance_branch_id_type_submitted_date_key;

CREATE UNIQUE INDEX idx_remittance_submitted_date
    ON remittance (branch_id, type, submitted_date)
    WHERE status = 'SUBMITTED';

-- =============================================================================
-- V18__index_day_foreign_keys.sql
--
-- Foreign-key columns the day, Finance and Today queries filter on had no index,
-- so daily_sales_summary (and the repositories behind Finance and Today) seq-scanned
-- session, product_sale, expense, allowance, attendance and remittance_line once per
-- branch day. Measured on 14,600 branch days / 292,000 sessions: one day 38.8 ms,
-- a 31-day list 1,212 ms.
--
-- Postgres does not index the referencing side of a foreign key on its own.
-- Only columns the code filters on are indexed; the "who did it" columns
-- (assigned_by, created_by, ...) are never queried by value and stay bare.
-- =============================================================================

CREATE INDEX idx_session_branch_day          ON session (branch_day_id);
CREATE INDEX idx_product_sale_branch_day     ON product_sale (branch_day_id);
CREATE INDEX idx_product_sale_session        ON product_sale (session_id) WHERE session_id IS NOT NULL;
CREATE INDEX idx_expense_branch_day          ON expense (branch_day_id);
CREATE INDEX idx_allowance_branch_day        ON allowance (branch_day_id);
CREATE INDEX idx_compensation_paying_day     ON compensation (paying_branch_day_id);
CREATE INDEX idx_attendance_branch_day       ON attendance (branch_day_id);
CREATE INDEX idx_remittance_line_remittance  ON remittance_line (remittance_id);

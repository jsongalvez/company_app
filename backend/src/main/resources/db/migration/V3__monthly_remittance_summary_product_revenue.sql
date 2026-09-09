-- =============================================================================
-- V3__monthly_remittance_summary_product_revenue.sql — #688
--
-- monthly_remittance_summary summed only remittance_financial_snapshot, which
-- exists solely for SESSION remittances (RemittancePolicy.requiresSnapshot).
-- PRODUCT remittances carry revenue in remittance_line rows with no snapshot,
-- so a PRODUCT-only month reported product_count > 0 with zero financials,
-- disagreeing with per-remittance detail (sumAmountsByRemittanceId).
--
-- Fix: add line-derived PRODUCT revenue to the view. Per-remittance extra is
--   PRODUCT_SALE lines of any SUBMITTED parent (never in a snapshot) plus
--   SESSION lines whose parent is a SUBMITTED PRODUCT remittance (no snapshot
--   exists to hold them). SESSION lines of SESSION parents stay snapshot-only
--   to avoid double-counting. Compensation/expenses stay snapshot-only:
--   PRODUCT revenue is unit price x quantity with no comp/expense deduction
--   (business-requirements: product remittance flow), and SESSION snapshots
--   already hold their days' comp/expense (overlap exclusion is type-scoped,
--   so shared days deduct once).
-- =============================================================================

CREATE OR REPLACE VIEW monthly_remittance_summary AS
SELECT
    r.branch_id,
    EXTRACT(YEAR FROM r.submitted_date)::INT AS year,
    EXTRACT(MONTH FROM r.submitted_date)::INT AS month,
    COUNT(*)::INT AS total_remittances,
    COUNT(*) FILTER (WHERE r.type = 'SESSION')::INT AS session_count,
    COUNT(*) FILTER (WHERE r.type = 'PRODUCT')::INT AS product_count,
    (
        COALESCE(SUM(rfs.gross_income), 0) +
        COALESCE(SUM(product_lines.extra_revenue), 0)
    )::NUMERIC(10,2) AS gross_income,
    COALESCE(SUM(rfs.total_compensation), 0)::NUMERIC(10,2) AS total_compensation,
    COALESCE(SUM(rfs.total_expenses), 0)::NUMERIC(10,2) AS total_expenses,
    (
        COALESCE(SUM(rfs.net_income), 0) +
        COALESCE(SUM(product_lines.extra_revenue), 0)
    )::NUMERIC(10,2) AS net_income
FROM remittance r
LEFT JOIN remittance_financial_snapshot rfs ON rfs.remittance_id = r.id
LEFT JOIN (
    SELECT rl.remittance_id, SUM(rl.amount) AS extra_revenue
    FROM remittance_line rl
    JOIN remittance parent ON parent.id = rl.remittance_id
    WHERE rl.deleted_at IS NULL
      AND parent.status = 'SUBMITTED'
      AND (rl.type = 'PRODUCT_SALE' OR parent.type = 'PRODUCT')
    GROUP BY rl.remittance_id
) product_lines ON product_lines.remittance_id = r.id
WHERE r.status = 'SUBMITTED'
GROUP BY r.branch_id, EXTRACT(YEAR FROM r.submitted_date), EXTRACT(MONTH FROM r.submitted_date);

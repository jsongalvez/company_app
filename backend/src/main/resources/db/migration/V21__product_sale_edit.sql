-- =============================================================================
-- V21__product_sale_edit.sql
--
-- User (owed #21): "wait, you cant edit product sales?" A product sale is now
-- edited, deleted and restored like an expense: soft delete (deleted_by,
-- deleted_at, deleted_reason) and an optimistic version. A deleted sale leaves
-- every total, as an expense does.
--
-- Stock given back by an edit or a delete is an ADJUSTMENT movement that names
-- its sale, so sale_id_logic lets ADJUSTMENT carry product_sale_id too.
-- =============================================================================

ALTER TABLE product_sale
    ADD COLUMN deleted_by     UUID        REFERENCES app_user(id),
    ADD COLUMN deleted_at     TIMESTAMPTZ,
    ADD COLUMN deleted_reason TEXT,
    ADD COLUMN version        INT         NOT NULL DEFAULT 1;

ALTER TABLE inventory_movement DROP CONSTRAINT sale_id_logic;
ALTER TABLE inventory_movement ADD CONSTRAINT sale_id_logic CHECK (
    (reason = 'SALE'       AND product_sale_id IS NOT NULL AND quantity_change < 0) OR
    (reason = 'ADJUSTMENT') OR
    (reason NOT IN ('SALE', 'ADJUSTMENT') AND product_sale_id IS NULL)
);

CREATE OR REPLACE VIEW daily_sales_summary AS
SELECT
    bd.id AS branch_day_id,
    bd.branch_id,
    bd.date,
    COALESCE((
        SELECT SUM(s.final_price)
        FROM session s
        LEFT JOIN active_session_voids sv ON sv.session_id = s.id
        WHERE s.branch_day_id = bd.id
          AND s.session_status = 'COMPLETED'
          AND sv.id IS NULL
    ), 0)::NUMERIC(10,2) AS gross_income,
    COALESCE((
        SELECT SUM(c.amount)
        FROM compensation c
        WHERE c.paying_branch_day_id = bd.id
    ), 0)::NUMERIC(10,2) AS total_compensation,
    COALESCE((
        SELECT SUM(e.amount)
        FROM expense e
        WHERE e.branch_day_id = bd.id AND e.deleted_at IS NULL
    ), 0)::NUMERIC(10,2) AS total_expenses,
    COALESCE((
        SELECT SUM(ps.total_amount_at_time)
        FROM product_sale ps
        LEFT JOIN active_session_voids sv ON sv.session_id = ps.session_id
        WHERE ps.branch_day_id = bd.id
          AND ps.deleted_at IS NULL
          AND sv.id IS NULL
    ), 0)::NUMERIC(10,2) AS total_product_sales,
    COALESCE((
        SELECT SUM(cs.amount)
        FROM commission_split cs
        WHERE cs.branch_day_id = bd.id
    ), 0)::NUMERIC(15,4) AS total_commission
FROM branch_day bd;

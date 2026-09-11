-- =============================================================================
-- V4__daily_product_sales_exclude_voided.sql — #858
--
-- daily_sales_summary.total_product_sales summed product_sale rows per day with
-- no void join, while gross_income excludes voided sessions (LEFT JOIN
-- active_session_voids + sv.id IS NULL) and commission input excludes
-- voided-session sales (findNonVoidedSalesByBranchDayInTransaction). A voided
-- session therefore dropped its commission to zero while its product sales
-- stayed counted in daily revenue and remittable as PRODUCT lines.
--
-- Business rule (business-requirements.md, Session Voiding): a voided session
-- "is excluded from all financial calculations". Fix: the product leg gains
-- the same void join. Null-session sales (walk-in without session link) are
-- kept: the LEFT JOIN yields NULL on the void side and passes the IS NULL
-- filter. Unvoided rows drop out of active_session_voids, so the sale returns
-- to the total on unvoid with no further write.
-- =============================================================================

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
          AND sv.id IS NULL
    ), 0)::NUMERIC(10,2) AS total_product_sales,
    COALESCE((
        SELECT SUM(cs.amount)
        FROM commission_split cs
        WHERE cs.branch_day_id = bd.id
    ), 0)::NUMERIC(15,4) AS total_commission
FROM branch_day bd;

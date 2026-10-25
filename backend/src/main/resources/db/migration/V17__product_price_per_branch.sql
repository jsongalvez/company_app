-- Feedback a6946922: every product has its own list price and commission at each branch.
-- There is no shared fallback and no branch-wide commission. A value that was never set
-- is filled once from the product's current value (a branch-wide commission, where one was
-- set, wins for that branch).
ALTER TABLE branch_inventory ADD COLUMN commission_amount NUMERIC(10,2) CHECK (commission_amount >= 0);

UPDATE branch_inventory bi
SET unit_price        = COALESCE(bi.unit_price, p.unit_price),
    commission_amount = COALESCE(b.commission_amount, p.commission_amount)
FROM product p, branch b
WHERE p.id = bi.product_id
  AND b.id = bi.branch_id;

ALTER TABLE branch_inventory
    ALTER COLUMN unit_price SET NOT NULL,
    ALTER COLUMN commission_amount SET NOT NULL;
ALTER TABLE branch DROP COLUMN commission_amount;

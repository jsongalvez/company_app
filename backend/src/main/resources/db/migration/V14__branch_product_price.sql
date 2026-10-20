-- Feedback 503bf228: each branch can set its own list price and commission for a product.
-- NULL means the branch uses the shared price from the product list. Changes are audited
-- on branch_inventory, so Change history shows who set which price.
ALTER TABLE branch_inventory
    ADD COLUMN unit_price        NUMERIC(10,2) CHECK (unit_price >= 0),
    ADD COLUMN commission_amount NUMERIC(10,2) CHECK (commission_amount >= 0);

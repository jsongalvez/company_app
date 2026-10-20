-- Feedback 503bf228 follow-up: commission is one value per branch, used by every product
-- sold there (not per product). NULL means each product's shared commission applies.
ALTER TABLE branch ADD COLUMN commission_amount NUMERIC(10,2) CHECK (commission_amount >= 0);
ALTER TABLE branch_inventory DROP COLUMN commission_amount;

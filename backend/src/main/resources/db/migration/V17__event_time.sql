-- A save made offline is sent later: clock_in, clock_out and sold_at hold when it happened,
-- and these columns hold when the server got it. Rows before this migration arrived as they happened.
ALTER TABLE attendance ADD COLUMN clock_in_recorded_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE attendance ADD COLUMN clock_out_recorded_at TIMESTAMPTZ;
UPDATE attendance SET clock_in_recorded_at = clock_in, clock_out_recorded_at = clock_out;
ALTER TABLE product_sale ADD COLUMN recorded_at TIMESTAMPTZ NOT NULL DEFAULT now();
UPDATE product_sale SET recorded_at = sold_at;

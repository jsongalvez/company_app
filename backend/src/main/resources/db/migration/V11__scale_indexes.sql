-- Indexes for the lookups that read whole tables as the data grows (tools/loadtest, numbers at 1000x in
-- tools/loadtest/README.md).

-- Recent clients walks sessions newest first instead of grouping every session by client.
CREATE INDEX idx_session_created_at ON session (created_at DESC, id DESC);

-- The offline client directory takes the first 5000 live clients in name order.
CREATE INDEX idx_client_name_live ON client (last_name, first_name) WHERE deleted_at IS NULL;

-- Client search ORs the name trigram match with a phone prefix; one unindexed branch turns the whole OR into a full scan.
CREATE INDEX idx_client_phone_prefix ON client (phone_number varchar_pattern_ops);

-- A client's history lists their walk-in product sales.
CREATE INDEX idx_product_sale_client ON product_sale (client_id) WHERE client_id IS NOT NULL;

-- My history lists a user's shifts.
CREATE INDEX idx_attendance_user ON attendance (user_id);

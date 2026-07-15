CREATE INDEX IF NOT EXISTS idx_client_middle_name_trgm ON client USING gin (middle_name gin_trgm_ops);

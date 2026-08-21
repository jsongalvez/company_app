-- The composite GIN trigram index covers each name-column predicate used by
-- ClientRepository.search. The single-column indexes add write and storage
-- overhead without changing the representative search plan.
DROP INDEX IF EXISTS idx_client_first_name_trgm;
DROP INDEX IF EXISTS idx_client_last_name_trgm;

-- Rollback:
-- CREATE INDEX idx_client_first_name_trgm ON client USING gin (first_name gin_trgm_ops);
-- CREATE INDEX idx_client_last_name_trgm ON client USING gin (last_name gin_trgm_ops);

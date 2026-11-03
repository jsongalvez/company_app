-- A session's history finds its people and voids by the session id inside their values
-- (AuditLogStore.recordAndParts). Without this the lookup reads every audit row of those
-- tables: 37-66ms at 100k rows, growing with the log; with it, under 1ms.
CREATE INDEX idx_audit_session_id ON audit_log ((COALESCE(new_value, old_value) ->> 'sessionId'));

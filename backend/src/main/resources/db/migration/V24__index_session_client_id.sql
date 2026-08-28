-- #430 — client directory session counts group all session history by client.
CREATE INDEX idx_session_client_id ON session (client_id);

-- What people did in the app (screens, taps, saves, API calls), uploaded in batches by the web app,
-- so a problem can be traced and replayed. No names or typed text: taps carry a hash of the label,
-- text fields only their length. Rows older than 14 days, or past the row cap, are deleted by the app.
CREATE TABLE telemetry_event (
    session_id  uuid NOT NULL,
    seq         integer NOT NULL CONSTRAINT telemetry_event_seq CHECK (seq >= 0),
    user_id     uuid NOT NULL,
    at          timestamptz NOT NULL,
    kind        varchar(10) NOT NULL,
    data        text NOT NULL CONSTRAINT telemetry_event_data_length CHECK (char_length(data) <= 1000),
    received_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, seq)
);
CREATE INDEX telemetry_event_user_at ON telemetry_event (user_id, at);
CREATE INDEX telemetry_event_received_at ON telemetry_event (received_at);

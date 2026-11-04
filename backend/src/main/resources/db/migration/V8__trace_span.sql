-- The server's own traces (OpenTelemetry spans), written by the app in batches off the request path.
-- A request's root span (parent_id NULL) is its wide event: route, status, outcome, masked user,
-- branch, statement count and time, error class. No names, request bodies, query strings or
-- exception messages; attrs is JSON text (read it as attrs::jsonb). Rows older than 14 days, or past the row cap, are deleted by the app.
CREATE TABLE trace_span (
    trace_id    char(32) NOT NULL,
    span_id     char(16) NOT NULL,
    parent_id   char(16),
    name        varchar(160) NOT NULL,
    start_at    timestamptz NOT NULL,
    duration_us bigint NOT NULL,
    error       boolean NOT NULL,
    attrs       text NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (trace_id, span_id)
);
CREATE INDEX trace_span_start_at ON trace_span (start_at);
CREATE INDEX trace_span_root_name ON trace_span (name, start_at) WHERE parent_id IS NULL;
CREATE INDEX trace_span_received_at ON trace_span (received_at);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tablespace WHERE spcname = 'telemetry')
       AND has_tablespace_privilege('telemetry', 'CREATE') THEN
        ALTER TABLE trace_span SET TABLESPACE telemetry;
        ALTER INDEX trace_span_pkey SET TABLESPACE telemetry;
        ALTER INDEX trace_span_start_at SET TABLESPACE telemetry;
        ALTER INDEX trace_span_root_name SET TABLESPACE telemetry;
        ALTER INDEX trace_span_received_at SET TABLESPACE telemetry;
    END IF;
END $$;

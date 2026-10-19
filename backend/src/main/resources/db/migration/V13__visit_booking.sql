-- Future visits booked ahead (feedback e4ec363c): a client is expected at a branch on a day,
-- without making a session for today. Any branch member books, moves or cancels; every change
-- is audited so the change history shows who did it.
CREATE TABLE visit_booking (
    id          uuid PRIMARY KEY,
    branch_id   uuid NOT NULL REFERENCES branch (id),
    client_id   uuid NOT NULL REFERENCES client (id),
    visit_date  date NOT NULL,
    visit_time  time,
    note        text CONSTRAINT visit_booking_note_length CHECK (char_length(note) <= 500),
    status      varchar(20) NOT NULL DEFAULT 'BOOKED'
        CONSTRAINT visit_booking_status CHECK (status IN ('BOOKED', 'CAME', 'CANCELLED')),
    created_by  uuid NOT NULL REFERENCES app_user (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid REFERENCES app_user (id),
    updated_at  timestamptz
);
CREATE INDEX visit_booking_branch_date ON visit_booking (branch_id, visit_date) WHERE status = 'BOOKED';
CREATE INDEX visit_booking_client ON visit_booking (client_id);

CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ----------------------------
-- ENUMS
-- ----------------------------

CREATE TYPE user_status               AS ENUM ('ACTIVE', 'INACTIVE');
CREATE TYPE capability_context_type   AS ENUM ('GLOBAL', 'BRANCH', 'BRANCH_DAY', 'MEDICAL_MISSION', 'PROVINCIAL_TOUR');
CREATE TYPE capability_source_type    AS ENUM ('RELIEF_ACCESS', 'MEDICAL_MISSION_DELEGATE', 'MANUAL_OVERRIDE', 'SYSTEM');
CREATE TYPE branch_type               AS ENUM ('CLINIC', 'PROVINCIAL_TOUR', 'MEDICAL_MISSION');
CREATE TYPE relief_status             AS ENUM ('PENDING', 'GRANTED', 'DENIED');
CREATE TYPE day_status                AS ENUM ('OPEN', 'PAST', 'REMITTED');
CREATE TYPE session_type              AS ENUM ('REGULAR', 'SECOND_SESSION', 'SUBSEQUENT', 'PROVINCIAL_FIRST', 'MEDICAL_MISSION');
CREATE TYPE session_status            AS ENUM ('PENDING', 'COMPLETED', 'NO_SHOW', 'CANCELLED');
CREATE TYPE inventory_movement_reason AS ENUM ('SALE', 'TESTER', 'SAMPLE', 'MISSING', 'RESTOCK', 'ADJUSTMENT');
CREATE TYPE remittance_type           AS ENUM ('SESSION', 'PRODUCT');
CREATE TYPE remittance_method         AS ENUM ('BANK_TRANSFER', 'HANDED_TO_ACCOUNTANT');
CREATE TYPE remittance_status         AS ENUM ('DRAFT', 'SUBMITTED');
CREATE TYPE remittance_line_type      AS ENUM ('SESSION', 'PRODUCT_SALE');
CREATE TYPE audit_action              AS ENUM ('INSERT', 'UPDATE', 'DELETE');
CREATE TYPE expense_category          AS ENUM (
    'PANTRY', 'COMMUNICATION', 'WATER', 'TRANSPORTATION',
    'ELECTRICITY', 'RENTAL', 'OFFICE_SUPPLIES', 'FURNITURE_FIXTURES', 'MISCELLANEOUS'
);

-- ----------------------------
-- ROLES & CAPABILITIES
-- ----------------------------

CREATE TABLE app_user (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(60)  NOT NULL,
    status        user_status  NOT NULL DEFAULT 'ACTIVE',
    email         TEXT         NOT NULL UNIQUE,
    display_name  TEXT         NOT NULL DEFAULT 'User'
);

CREATE TABLE role (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE capability (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE
);

CREATE TABLE role_capability (
    role_id       UUID NOT NULL REFERENCES role(id),
    capability_id UUID NOT NULL REFERENCES capability(id),
    PRIMARY KEY (role_id, capability_id)
);

CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES app_user(id),
    role_id UUID NOT NULL REFERENCES role(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_capability (
    id            UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID                     NOT NULL REFERENCES app_user(id),
    capability_id UUID                     NOT NULL REFERENCES capability(id),
    context_type  capability_context_type  NOT NULL,
    context_id    UUID                     NOT NULL,
    valid_from    TIMESTAMPTZ              NOT NULL DEFAULT now(),
    valid_to      TIMESTAMPTZ,
    source_type   capability_source_type   NOT NULL,
    source_id     UUID                     NOT NULL,
    priority      SMALLINT                 NOT NULL DEFAULT 0,
    CONSTRAINT valid_range CHECK (valid_to IS NULL OR valid_from <= valid_to)
);

-- Capability lookup: user + capability + context window
CREATE INDEX idx_user_cap_lookup ON user_capability (user_id, capability_id, context_type, context_id);
-- FIX (#7 original / time-filter path): index on valid_to for efficient time-window filtering
CREATE INDEX idx_user_cap_time   ON user_capability (user_id, valid_to);

-- ----------------------------
-- BRANCHES
-- ----------------------------

CREATE TABLE branch (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_type branch_type NOT NULL DEFAULT 'CLINIC',
    name        TEXT        NOT NULL,
    UNIQUE (branch_type, name)
);

CREATE TABLE user_branch_assignment (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES app_user(id),
    branch_id   UUID        NOT NULL REFERENCES branch(id),
    slot        SMALLINT    NOT NULL DEFAULT 0,
    assigned_by UUID        NOT NULL REFERENCES app_user(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at    TIMESTAMPTZ
);
-- One active assignment per user per branch
CREATE UNIQUE INDEX idx_one_active_assignment ON user_branch_assignment (user_id, branch_id) WHERE ended_at IS NULL;

-- ----------------------------
-- DAY STATE
-- ----------------------------

CREATE TABLE branch_day (
    id        UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID       NOT NULL REFERENCES branch(id),
    date      DATE       NOT NULL,
    status    day_status NOT NULL DEFAULT 'OPEN',
    UNIQUE (branch_id, date)
);

-- ----------------------------
-- ATTENDANCE
-- Note: attendance_history dropped (Issue #7). Historical reads
-- go against the attendance table directly; audit_log covers edits.
-- ----------------------------

CREATE TABLE attendance (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_day_id UUID        NOT NULL REFERENCES branch_day(id),
    user_id       UUID        NOT NULL REFERENCES app_user(id),
    marked_by     UUID        NOT NULL REFERENCES app_user(id),
    clock_in      TIMESTAMPTZ NOT NULL DEFAULT now(),
    clock_out     TIMESTAMPTZ
);
-- Issue #6: Multiple shifts allowed; only one open window at a time
CREATE UNIQUE INDEX idx_one_active_clock_in ON attendance (user_id, branch_day_id) WHERE clock_out IS NULL;

CREATE TABLE branch_day_assignment (
    id            UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_day_id UUID    NOT NULL REFERENCES branch_day(id),
    user_id       UUID    NOT NULL REFERENCES app_user(id),
    is_relief     BOOLEAN NOT NULL,
    UNIQUE (branch_day_id, user_id)
);

-- ----------------------------
-- RELIEF & MISSION
-- ----------------------------

CREATE TABLE grant_relief_access (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_day_id  UUID          NOT NULL REFERENCES branch_day(id),
    requested_by   UUID          NOT NULL REFERENCES app_user(id),
    request_status relief_status NOT NULL DEFAULT 'PENDING',
    target_user    UUID          NOT NULL REFERENCES app_user(id),
    granted_by     UUID          REFERENCES app_user(id),
    granted_at     TIMESTAMPTZ,
    CONSTRAINT granted_logic CHECK (
        (request_status = 'GRANTED' AND granted_by IS NOT NULL AND granted_at IS NOT NULL)
        OR (request_status != 'GRANTED')
    )
);
-- Issue #4: One granted access per requesting user per branch day
CREATE UNIQUE INDEX idx_one_grant_per_day ON grant_relief_access (requested_by, branch_day_id) WHERE request_status = 'GRANTED';

CREATE TABLE medical_mission_delegate (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    target_user UUID        NOT NULL REFERENCES app_user(id),
    branch_id   UUID        NOT NULL REFERENCES branch(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by UUID        NOT NULL REFERENCES app_user(id),
    ended_at    TIMESTAMPTZ
);

-- ----------------------------
-- CLIENTS
-- ----------------------------

CREATE TABLE client (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name         TEXT        NOT NULL,
    last_name          TEXT        NOT NULL,
    middle_name        TEXT,
    suffix             TEXT,
    phone_number       VARCHAR(20),
    address            TEXT        NOT NULL DEFAULT 'N/A',
    gender             CHAR(1)     NOT NULL CHECK (gender IN ('M', 'F')),
    age                INT         NOT NULL CHECK (age BETWEEN 0 AND 120),
    systolic_bp        SMALLINT    CHECK (systolic_bp BETWEEN 40 AND 300),
    diastolic_bp       SMALLINT    CHECK (diastolic_bp BETWEEN 20 AND 200),
    medical_conditions TEXT,
    deleted_at         TIMESTAMPTZ,
    CONSTRAINT bp_both_or_none CHECK (
           (systolic_bp IS NULL     AND diastolic_bp IS NULL)
        OR (systolic_bp IS NOT NULL AND diastolic_bp IS NOT NULL)
    )
);
CREATE INDEX IF NOT EXISTS idx_client_first_name_trgm ON client USING gin (first_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_client_last_name_trgm ON client USING gin (last_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_client_trgm ON client USING gin (first_name gin_trgm_ops, middle_name gin_trgm_ops, last_name gin_trgm_ops);

-- ----------------------------
-- SESSIONS
-- ----------------------------

CREATE TABLE session (
    id                        UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                 UUID           NOT NULL REFERENCES client(id),
    branch_day_id             UUID           NOT NULL REFERENCES branch_day(id),
    requested_practitioner_id UUID           REFERENCES app_user(id),
    session_type              session_type   NOT NULL,
    is_walk_in                BOOLEAN        NOT NULL,
    session_status            session_status NOT NULL DEFAULT 'PENDING',
    base_price                NUMERIC(10,2)  NOT NULL,
    final_price               NUMERIC(10,2)  NOT NULL,
    remarks                   TEXT,
    other_concerns            TEXT,
    booked_at                 TIMESTAMPTZ,
    next_appointment_date     DATE,
    created_at                TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version                   INT            NOT NULL DEFAULT 1,
    -- Walk-ins cannot be NO_SHOW or CANCELLED
    CONSTRAINT walk_in_status CHECK (
        NOT (is_walk_in = true AND session_status IN ('NO_SHOW', 'CANCELLED'))
    ),
    -- Issue #12: next_appointment_date nullable-safe check
    -- If both are set, appointment must not be before the booking date.
    -- Walk-ins (booked_at IS NULL) may freely set a next appointment.
    CONSTRAINT appointment_future CHECK (
        next_appointment_date IS NULL
        OR booked_at IS NULL
        OR next_appointment_date >= booked_at::date
    )
);
-- Issue #11: Only one PENDING session per client globally
CREATE UNIQUE INDEX idx_client_one_pending_session ON session (client_id) WHERE session_status = 'PENDING';

CREATE TABLE session_void (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID        NOT NULL REFERENCES session(id) UNIQUE,
    voided_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    voided_by       UUID        NOT NULL REFERENCES app_user(id),
    void_reason     TEXT        NOT NULL,
    unvoided_at     TIMESTAMPTZ,
    unvoided_by     UUID        REFERENCES app_user(id),
    unvoided_reason TEXT,
    CONSTRAINT unvoid_logic CHECK (
        (unvoided_at IS NULL AND unvoided_by IS NULL AND unvoided_reason IS NULL)
        OR (unvoided_at IS NOT NULL AND unvoided_by IS NOT NULL AND unvoided_reason IS NOT NULL)
    )
);

CREATE TABLE session_base_rate (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    set_by         UUID          NOT NULL REFERENCES app_user(id),
    branch_id      UUID          NOT NULL REFERENCES branch(id),
    session_type   session_type  NOT NULL,
    rate           NUMERIC(10,2) NOT NULL,
    effective_from TIMESTAMPTZ   NOT NULL,
    effective_until TIMESTAMPTZ  NOT NULL,
    -- Issue #10: Prevent overlapping rates for same branch + session type
    -- NOTE: effective_from and effective_until must be stored in UTC.
    -- Application must convert from Asia/Manila before insert.
    CONSTRAINT no_rate_overlap EXCLUDE USING gist (
        branch_id    WITH =,
        session_type WITH =,
        tstzrange(effective_from, effective_until, '[]') WITH &&
    )
);

CREATE TABLE session_practitioner (
    id              UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID     NOT NULL REFERENCES session(id),
    practitioner_id UUID     NOT NULL REFERENCES app_user(id),
    remarks         TEXT,
    slot_at_time    SMALLINT NOT NULL,
    UNIQUE (session_id, practitioner_id)
);

-- Issue #14: Concern promotion — created_by/at added for provenance
CREATE TABLE concern (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    label      TEXT        NOT NULL,
    created_by UUID        REFERENCES app_user(id),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE session_concern (
    session_id UUID NOT NULL REFERENCES session(id),
    concern_id UUID NOT NULL REFERENCES concern(id),
    PRIMARY KEY (session_id, concern_id)
);

-- ----------------------------
-- PRODUCTS & INVENTORY
-- ----------------------------

CREATE TABLE product_category (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE product (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name                TEXT          NOT NULL,
    product_category_id UUID          NOT NULL REFERENCES product_category(id),
    is_active           BOOLEAN       NOT NULL DEFAULT true,
    unit_price          NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
    commission_amount   NUMERIC(10,2) NOT NULL CHECK (commission_amount >= 0)
);

CREATE TABLE branch_inventory (
    id            UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id     UUID    NOT NULL REFERENCES branch(id),
    product_id    UUID    NOT NULL REFERENCES product(id),
    current_stock INT     NOT NULL DEFAULT 0 CHECK (current_stock >= 0),
    version       INT     NOT NULL DEFAULT 1,
    UNIQUE (branch_id, product_id)
);

CREATE TABLE product_sale (
    id                        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_day_id             UUID          NOT NULL REFERENCES branch_day(id),
    session_id                UUID          REFERENCES session(id),
    client_id                 UUID          REFERENCES client(id),
    is_walk_in                BOOLEAN       NOT NULL DEFAULT false,
    product_id                UUID          NOT NULL REFERENCES product(id),
    product_name              TEXT          NOT NULL,
    handled_by                UUID          NOT NULL REFERENCES app_user(id),
    quantity                  SMALLINT      NOT NULL CHECK (quantity >= 1),
    unit_price_at_time        NUMERIC(10,2) NOT NULL,
    total_amount_at_time      NUMERIC(10,2) NOT NULL,
    commission_amount_at_time NUMERIC(15,4) NOT NULL,
    sold_at                   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT sale_type_logic CHECK (
        (session_id IS NOT NULL AND client_id IS NULL     AND is_walk_in = FALSE) OR
        (session_id IS NULL     AND client_id IS NOT NULL AND is_walk_in = TRUE)  OR
        (session_id IS NULL     AND client_id IS NULL     AND is_walk_in = TRUE)
    )
);

CREATE TABLE inventory_movement (
    id              UUID                      PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID                      NOT NULL REFERENCES product(id),
    product_sale_id UUID                      REFERENCES product_sale(id),
    branch_id       UUID                      NOT NULL REFERENCES branch(id),
    branch_day_id   UUID                      NOT NULL REFERENCES branch_day(id), -- Issue #15: Day tie for state machine enforcement
    reason          inventory_movement_reason NOT NULL,
    quantity_change INT                       NOT NULL CHECK (quantity_change != 0),
    moved_by        UUID                      NOT NULL REFERENCES app_user(id),
    moved_at        TIMESTAMPTZ               NOT NULL DEFAULT now(),
    notes           TEXT,
    -- Issue #16: Enforce sign per movement type. ADJUSTMENT allows both signs.
    CONSTRAINT quantity_sign CHECK (
        (reason = 'RESTOCK'                                    AND quantity_change > 0) OR
        (reason IN ('SALE', 'TESTER', 'SAMPLE', 'MISSING')    AND quantity_change < 0) OR
        (reason = 'ADJUSTMENT')
    ),
    -- Issue #17: MISSING movements must have a non-empty note
    CONSTRAINT missing_notes CHECK (
        reason != 'MISSING' OR (notes IS NOT NULL AND length(notes) > 0)
    ),
    CONSTRAINT sale_id_logic CHECK (
        (reason = 'SALE'  AND product_sale_id IS NOT NULL AND quantity_change < 0) OR
        (reason != 'SALE' AND product_sale_id IS NULL)
    )
);

-- ----------------------------
-- FINANCE
-- ----------------------------

CREATE TABLE compensation (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    work_branch_day_id   UUID          NOT NULL REFERENCES branch_day(id),
    paying_branch_day_id UUID          NOT NULL REFERENCES branch_day(id),
    user_id              UUID          NOT NULL REFERENCES app_user(id),
    amount               NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    assigned_by          UUID          NOT NULL REFERENCES app_user(id),
    assigned_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    note                 TEXT
);
-- Issue #18: One payout per user per paying branch per day
CREATE UNIQUE INDEX idx_compensation_unique ON compensation (user_id, paying_branch_day_id);

CREATE TABLE commission_split (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_day_id UUID          NOT NULL REFERENCES branch_day(id),
    user_id       UUID          NOT NULL REFERENCES app_user(id),
    amount        NUMERIC(15,4) NOT NULL CHECK (amount >= 0),
    UNIQUE (branch_day_id, user_id)
);

CREATE TABLE commission_manual_inclusion (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_sale_id UUID        NOT NULL REFERENCES product_sale(id),
    user_id         UUID        NOT NULL REFERENCES app_user(id),
    is_included     BOOLEAN     NOT NULL,
    reason          TEXT,
    assigned_by     UUID        NOT NULL REFERENCES app_user(id),
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (product_sale_id, user_id)
);

CREATE TABLE allowance (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_day_id UUID          NOT NULL REFERENCES branch_day(id),
    user_id       UUID          NOT NULL REFERENCES app_user(id),
    amount        NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    assigned_by   UUID          NOT NULL REFERENCES app_user(id),
    assigned_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE expense (
    id            UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_day_id UUID             NOT NULL REFERENCES branch_day(id),
    amount        NUMERIC(10,2)    NOT NULL CHECK (amount > 0),
    category      expense_category NOT NULL,
    notes         TEXT,
    created_by    UUID             NOT NULL REFERENCES app_user(id),
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    deleted_by    UUID             REFERENCES app_user(id),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT expense_deleted_logic CHECK (
        (deleted_by IS NULL AND deleted_at IS NULL)
        OR (deleted_by IS NOT NULL AND deleted_at IS NOT NULL)
    )
);

-- ----------------------------
-- REMITTANCE
-- ----------------------------

CREATE TABLE remittance (
    id               UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    type             remittance_type   NOT NULL,
    status           remittance_status NOT NULL DEFAULT 'DRAFT',
    branch_id        UUID              NOT NULL REFERENCES branch(id),
    method           remittance_method NOT NULL,
    -- submitted_date: the calendar date this remittance was submitted.
    -- Distinct from date_range_end. Named explicitly to avoid confusion.
    submitted_date   DATE              NOT NULL,
    date_range_start DATE              NOT NULL,
    date_range_end   DATE              NOT NULL,
    submitted_by     UUID              NOT NULL REFERENCES app_user(id),
    created_at       TIMESTAMPTZ       NOT NULL DEFAULT now(),
    version          INT               NOT NULL DEFAULT 1,
    UNIQUE (branch_id, type, submitted_date),
    CONSTRAINT range_valid CHECK (date_range_start <= date_range_end),
    -- Issue #2 (remittance): Exclusion constraint scoped to SUBMITTED only.
    -- DRAFTs are excluded so coordinators can create, discard, and recreate
    -- drafts without hitting the overlap constraint.
    -- Requires PostgreSQL 14+ for WHERE predicate on EXCLUDE.
    CONSTRAINT no_remittance_overlap EXCLUDE USING gist (
        branch_id WITH =,
        type      WITH =,
        daterange(date_range_start, date_range_end, '[]') WITH &&
    ) WHERE (status = 'SUBMITTED')
);

CREATE TABLE remittance_day_breakdown (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    remittance_id UUID NOT NULL REFERENCES remittance(id),
    branch_day_id UUID NOT NULL REFERENCES branch_day(id),
    UNIQUE (remittance_id, branch_day_id)
);

CREATE TABLE remittance_financial_snapshot (
    remittance_id      UUID          PRIMARY KEY REFERENCES remittance(id),
    gross_income       NUMERIC(10,2) NOT NULL,
    total_compensation NUMERIC(10,2) NOT NULL,
    total_expenses     NUMERIC(10,2) NOT NULL,
    net_income         NUMERIC(10,2) NOT NULL,
    snapshotted_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Issue #20: Immutability trigger — snapshot is write-once at submission.
-- The service layer must not insert until status transitions to SUBMITTED.
CREATE OR REPLACE FUNCTION fn_remittance_snapshot_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'remittance_financial_snapshot is immutable. Record for remittance_id % cannot be modified or deleted.', OLD.remittance_id;
END;
$$;

CREATE TRIGGER trg_remittance_snapshot_immutable
BEFORE UPDATE OR DELETE ON remittance_financial_snapshot
FOR EACH ROW EXECUTE FUNCTION fn_remittance_snapshot_immutable();

CREATE TABLE remittance_line (
    id              UUID                 PRIMARY KEY DEFAULT gen_random_uuid(),
    remittance_id   UUID                 NOT NULL REFERENCES remittance(id),
    type            remittance_line_type NOT NULL,
    session_id      UUID                 REFERENCES session(id),
    product_sale_id UUID                 REFERENCES product_sale(id),
    created_by      UUID                 REFERENCES app_user(id),
    created_at      TIMESTAMPTZ          NOT NULL DEFAULT now(),
    deleted_by      UUID                 REFERENCES app_user(id),
    deleted_at      TIMESTAMPTZ,
    amount          NUMERIC(10,2)        NOT NULL,
    -- BUG FIX (#1): was `deleted_at IS NOT NULL AND deleted_at IS NOT NULL`
    -- Corrected to check deleted_by in the second branch.
    CONSTRAINT line_deleted_logic CHECK (
           (deleted_at IS NULL     AND deleted_by IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by IS NOT NULL)
    ),
    CONSTRAINT line_type_logic CHECK (
        (type = 'SESSION'      AND session_id IS NOT NULL      AND product_sale_id IS NULL) OR
        (type = 'PRODUCT_SALE' AND product_sale_id IS NOT NULL AND session_id IS NULL)
    )
);
-- One active line per session / per product_sale (soft-delete aware)
CREATE UNIQUE INDEX idx_remittance_line_session ON remittance_line (session_id)      WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_remittance_line_product ON remittance_line (product_sale_id) WHERE deleted_at IS NULL;

-- ----------------------------
-- AUDIT
-- ----------------------------

CREATE TABLE audit_log (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name      TEXT         NOT NULL,
    record_id       UUID         NOT NULL,
    action          audit_action NOT NULL,
    changed_by      UUID         NOT NULL REFERENCES app_user(id),
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    old_value       JSONB,
    new_value       JSONB,
    is_flagged      BOOLEAN      NOT NULL DEFAULT false,
    reason          TEXT,
    acknowledged_by UUID         REFERENCES app_user(id),
    acknowledged_at TIMESTAMPTZ
);
CREATE INDEX idx_audit_lookup  ON audit_log (table_name, record_id);
CREATE INDEX idx_audit_time    ON audit_log (changed_at);
CREATE INDEX idx_audit_flagged ON audit_log (is_flagged) WHERE is_flagged = true;

-- ----------------------------
-- VIEWS (Issue #2, #5)
-- ----------------------------

-- active_session_voids
-- Always use this view — never query session_void directly for void status.
-- A row with unvoided_at IS NOT NULL means the void was reversed.
CREATE VIEW active_session_voids AS
SELECT *
FROM session_void
WHERE unvoided_at IS NULL;

-- active_user_capabilities
-- Issue #2 fix: DISTINCT ON now includes context_type to prevent cross-context collapse.
-- Issue #5 fix: Joins app_user to exclude INACTIVE users from all capability checks.
CREATE VIEW active_user_capabilities AS
SELECT DISTINCT ON (uc.user_id, uc.capability_id, uc.context_type, uc.context_id)
    uc.user_id,
    uc.capability_id,
    uc.context_type,
    uc.context_id,
    uc.priority,
    uc.source_type
FROM user_capability uc
JOIN app_user au ON uc.user_id = au.id
WHERE au.status = 'ACTIVE'
  AND now() BETWEEN uc.valid_from AND COALESCE(uc.valid_to, 'infinity'::timestamptz)
ORDER BY uc.user_id, uc.capability_id, uc.context_type, uc.context_id, uc.priority DESC;

-- ----------------------------
-- NOTIFICATIONS
-- ----------------------------

CREATE TABLE IF NOT EXISTS notification (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID        NOT NULL REFERENCES session(id),
    user_id    UUID        NOT NULL REFERENCES app_user(id),
    branch_id  UUID        NOT NULL REFERENCES branch(id),
    is_read    BOOLEAN     NOT NULL DEFAULT false,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_notification_unique
    ON notification (session_id, user_id);

CREATE INDEX IF NOT EXISTS idx_notification_unread
    ON notification (user_id) WHERE is_read = false;

-- daily_sales_summary
-- Aggregates daily financial data per branch day using correlated subqueries.
CREATE VIEW daily_sales_summary AS
SELECT
    bd.id AS branch_day_id,
    bd.branch_id,
    bd.date,
    COALESCE((
        SELECT SUM(s.final_price)
        FROM session s
        LEFT JOIN active_session_voids sv ON sv.session_id = s.id
        WHERE s.branch_day_id = bd.id
          AND s.session_status = 'COMPLETED'
          AND sv.id IS NULL
    ), 0)::NUMERIC(10,2) AS gross_income,
    COALESCE((
        SELECT SUM(c.amount)
        FROM compensation c
        WHERE c.paying_branch_day_id = bd.id
    ), 0)::NUMERIC(10,2) AS total_compensation,
    COALESCE((
        SELECT SUM(e.amount)
        FROM expense e
        WHERE e.branch_day_id = bd.id AND e.deleted_at IS NULL
    ), 0)::NUMERIC(10,2) AS total_expenses,
    COALESCE((
        SELECT SUM(ps.total_amount_at_time)
        FROM product_sale ps
        WHERE ps.branch_day_id = bd.id
    ), 0)::NUMERIC(10,2) AS total_product_sales,
    COALESCE((
        SELECT SUM(cs.amount)
        FROM commission_split cs
        WHERE cs.branch_day_id = bd.id
    ), 0)::NUMERIC(15,4) AS total_commission
FROM branch_day bd;

-- monthly_remittance_summary
-- Aggregates submitted remittance data per branch per month.
-- Financial snapshot columns apply only to SESSION remittances.
CREATE VIEW monthly_remittance_summary AS
SELECT
    r.branch_id,
    EXTRACT(YEAR FROM r.submitted_date)::INT AS year,
    EXTRACT(MONTH FROM r.submitted_date)::INT AS month,
    COUNT(*)::INT AS total_remittances,
    COUNT(*) FILTER (WHERE r.type = 'SESSION')::INT AS session_count,
    COUNT(*) FILTER (WHERE r.type = 'PRODUCT')::INT AS product_count,
    COALESCE(SUM(rfs.gross_income), 0)::NUMERIC(10,2) AS gross_income,
    COALESCE(SUM(rfs.total_compensation), 0)::NUMERIC(10,2) AS total_compensation,
    COALESCE(SUM(rfs.total_expenses), 0)::NUMERIC(10,2) AS total_expenses,
    COALESCE(SUM(rfs.net_income), 0)::NUMERIC(10,2) AS net_income
FROM remittance r
LEFT JOIN remittance_financial_snapshot rfs ON rfs.remittance_id = r.id
WHERE r.status = 'SUBMITTED'
GROUP BY r.branch_id, EXTRACT(YEAR FROM r.submitted_date), EXTRACT(MONTH FROM r.submitted_date);


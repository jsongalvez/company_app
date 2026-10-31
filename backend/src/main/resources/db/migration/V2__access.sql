-- =============================================================================
-- V2__access.sql: who can do what (launch set, Nov 1 2026)
--
-- Roles and capability codes are constants: the app names them by string
-- (shared/domain). Never add one at runtime. The matrix below is the single
-- source of what each role holds; active_user_capabilities turns it, branch
-- membership, relief and helper grants into the rights the app checks.
-- Business logic reads only that view, never user_role or role_capability.
-- Word list for each role and right: CONTEXT.md, "People and access".
-- =============================================================================

INSERT INTO role (name)
VALUES ('SUPERUSER'),
       ('OWNER'),
       ('MANAGER'),
       ('COORDINATOR'),
       ('PRACTITIONER'),
       ('ACCOUNTANT'),
       ('ONBOARDING');

INSERT INTO capability (code)
VALUES ('VIEW_BRANCH_DATA'),                -- see a branch's days
       ('EDIT_BRANCH_DATA'),                -- add and change on open days
       ('EDIT_PAST_DAY'),                   -- change closed and remitted days
       ('VOID_SESSION'),                    -- void and restore sessions
       ('SUBMIT_REMITTANCE'),               -- send and undo remittances
       ('ASSIGN_COMPENSATION'),             -- pay staff
       ('MANAGE_PRODUCTS'),                 -- restock and fix stock
       ('MANAGE_CATALOG'),                  -- products and categories (global)
       ('MANAGE_USERS'),                    -- users and roles (global)
       ('ASSIGN_DELEGATE'),                 -- mission helpers (global)
       ('VIEW_COMMISSION'),                 -- see members' commission
       ('ERASE_CLIENT'),                    -- erase a client's personal details (global)
       ('RECEIVE_NEXT_APPOINTMENT_ALERTS'); -- upcoming-session alerts

-- What each role holds. ONBOARDING holds nothing until given a real role.
-- Owners don't change closed days, void or remit; Coordinators do (business rules).
-- Commission is private to a branch: only the Superuser sees it everywhere;
-- members, relief and helpers get it at their branch through the view.
CREATE TEMP TABLE role_grant (role text, code text) ON COMMIT DROP;
INSERT INTO role_grant (role, code)
SELECT g.role, unnest(g.codes)
FROM (VALUES
    ('SUPERUSER',    ARRAY['VIEW_BRANCH_DATA', 'EDIT_BRANCH_DATA', 'EDIT_PAST_DAY', 'VOID_SESSION',
                           'SUBMIT_REMITTANCE', 'ASSIGN_COMPENSATION', 'MANAGE_PRODUCTS', 'MANAGE_CATALOG',
                           'MANAGE_USERS', 'ASSIGN_DELEGATE', 'VIEW_COMMISSION', 'ERASE_CLIENT']),
    ('OWNER',        ARRAY['VIEW_BRANCH_DATA', 'EDIT_BRANCH_DATA', 'ASSIGN_COMPENSATION', 'MANAGE_PRODUCTS',
                           'MANAGE_CATALOG', 'MANAGE_USERS', 'ASSIGN_DELEGATE', 'ERASE_CLIENT']),
    ('MANAGER',      ARRAY['VIEW_BRANCH_DATA', 'EDIT_BRANCH_DATA', 'EDIT_PAST_DAY', 'VOID_SESSION',
                           'SUBMIT_REMITTANCE', 'ASSIGN_COMPENSATION', 'MANAGE_PRODUCTS', 'MANAGE_CATALOG',
                           'MANAGE_USERS', 'ASSIGN_DELEGATE', 'ERASE_CLIENT']),
    ('COORDINATOR',  ARRAY['VIEW_BRANCH_DATA', 'EDIT_BRANCH_DATA', 'EDIT_PAST_DAY', 'VOID_SESSION',
                           'SUBMIT_REMITTANCE', 'ASSIGN_COMPENSATION', 'MANAGE_PRODUCTS', 'MANAGE_CATALOG',
                           'ERASE_CLIENT', 'RECEIVE_NEXT_APPOINTMENT_ALERTS']),
    ('PRACTITIONER', ARRAY['VIEW_BRANCH_DATA', 'EDIT_BRANCH_DATA', 'MANAGE_CATALOG']),
    ('ACCOUNTANT',   ARRAY['VIEW_BRANCH_DATA'])
) AS g(role, codes);

DO $$
DECLARE bad text;
BEGIN
    SELECT string_agg(g.role || ':' || g.code, ', ') INTO bad
    FROM role_grant g
    WHERE NOT EXISTS (SELECT 1 FROM role r WHERE r.name = g.role)
       OR NOT EXISTS (SELECT 1 FROM capability c WHERE c.code = g.code);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'role grant names an unknown role or code: %', bad;
    END IF;
END $$;

INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role_grant g
JOIN role r ON r.name = g.role
JOIN capability c ON c.code = g.code;

-- active_user_capabilities: the single place capability checks resolve (ADR-0023).
-- DISTINCT ON includes context_type to prevent cross-context collapse; INACTIVE
-- users are excluded everywhere; the time window handles expiry. Role-derived rows
-- carry priority 5 (GrantPriorities.ROLE_DERIVED: above the column default 0, below
-- every explicit grant, relief 10 / delegate 20 / direct 100; keep in sync with
-- GrantPriorities.kt) and GLOBAL rows use the all-zero UUID as context_id.
CREATE VIEW active_user_capabilities AS
SELECT DISTINCT ON (granted.user_id, granted.capability_id, granted.context_type, granted.context_id)
    granted.user_id,
    granted.capability_id,
    granted.context_type,
    granted.context_id,
    granted.priority,
    granted.source_type
FROM (
    -- (a) direct grants — user_capability rows, unchanged semantics.
    SELECT
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

    UNION ALL

    -- (a2) what a direct branch or day EDIT_BRANCH_DATA grant carries with it:
    --      VIEW_BRANCH_DATA in the same context. A day grant (relief) and a helper
    --      grant (Branches-page delegate, until removed) carry VOID_SESSION and
    --      VIEW_COMMISSION as well: a member's rights. A GLOBAL edit grant implies nothing.
    SELECT
        uc.user_id,
        implied.id AS capability_id,
        uc.context_type,
        uc.context_id,
        uc.priority,
        uc.source_type
    FROM user_capability uc
    JOIN app_user au ON uc.user_id = au.id
    JOIN capability granted_code ON granted_code.id = uc.capability_id
    JOIN capability implied ON implied.code = 'VIEW_BRANCH_DATA'
        OR (
            implied.code IN ('VOID_SESSION', 'VIEW_COMMISSION')
            AND (uc.context_type = 'BRANCH_DAY' OR uc.source_type = 'MEDICAL_MISSION_DELEGATE')
        )
    WHERE au.status = 'ACTIVE'
      AND granted_code.code = 'EDIT_BRANCH_DATA'
      AND uc.context_type <> 'GLOBAL'
      AND now() BETWEEN uc.valid_from AND COALESCE(uc.valid_to, 'infinity'::timestamptz)

    UNION ALL

    -- (b) role-derived GLOBAL grants: users and helpers for SUPERUSER/OWNER/MANAGER,
    --     pay everywhere for SUPERUSER/OWNER, all-branches VIEW_BRANCH_DATA for
    --     SUPERUSER/OWNER/ACCOUNTANT, erasing a client's personal details for
    --     Coordinator and up, and the catalog for everyone but Accountant/Onboarding.
    SELECT
        ur.user_id,
        rc.capability_id,
        'GLOBAL'::capability_context_type AS context_type,
        '00000000-0000-0000-0000-000000000000'::uuid AS context_id,
        5::smallint AS priority,
        'ROLE'::capability_source_type AS source_type
    FROM user_role ur
    JOIN app_user au ON au.id = ur.user_id
    JOIN role r ON r.id = ur.role_id
    JOIN role_capability rc ON rc.role_id = r.id
    JOIN capability c ON c.id = rc.capability_id
    WHERE au.status = 'ACTIVE'
      AND (
          (
              c.code IN ('MANAGE_USERS', 'ASSIGN_DELEGATE')
              AND r.name IN ('SUPERUSER', 'OWNER', 'MANAGER')
          )
          OR
          (
              c.code = 'ASSIGN_COMPENSATION'
              AND r.name IN ('SUPERUSER', 'OWNER')
          )
          OR
          (
              c.code = 'VIEW_BRANCH_DATA'
              AND r.name IN ('SUPERUSER', 'OWNER', 'ACCOUNTANT')
          )
          OR
          (
              c.code = 'ERASE_CLIENT'
              AND r.name IN ('SUPERUSER', 'OWNER', 'MANAGER', 'COORDINATOR')
          )
          OR
          (
              c.code = 'MANAGE_CATALOG'
              AND r.name IN ('SUPERUSER', 'OWNER', 'MANAGER', 'COORDINATOR', 'PRACTITIONER')
          )
      )

    UNION ALL

    -- (c) role-derived BRANCH grants (#417): every non-management code of each
    --     assigned role, at every branch holding an ACTIVE assignment.
    SELECT
        uba.user_id,
        rc.capability_id,
        'BRANCH'::capability_context_type AS context_type,
        uba.branch_id AS context_id,
        5::smallint AS priority,
        'ROLE'::capability_source_type AS source_type
    FROM user_branch_assignment uba
    JOIN app_user au ON au.id = uba.user_id
    JOIN user_role ur ON ur.user_id = uba.user_id
    JOIN role r ON r.id = ur.role_id
    JOIN role_capability rc ON rc.role_id = r.id
    JOIN capability c ON c.id = rc.capability_id
    WHERE au.status = 'ACTIVE'
      AND uba.ended_at IS NULL
      AND c.code NOT IN ('MANAGE_USERS', 'ASSIGN_DELEGATE', 'MANAGE_CATALOG', 'ERASE_CLIENT')

    UNION ALL

    -- (d) branch membership (owed #20): everyone with an ACTIVE assignment at a
    --     branch edits that branch's data the same way, whatever their role:
    --     view, add/edit/delete, void, and correct a mark. Remitted days, pay,
    --     remittance, users and catalog stay with the role grants above. Relief
    --     comes through (a) as day grants and gains nothing here. Someone whose
    --     only role is ONBOARDING is not working yet and stays out.
    SELECT
        uba.user_id,
        c.id AS capability_id,
        'BRANCH'::capability_context_type AS context_type,
        uba.branch_id AS context_id,
        5::smallint AS priority,
        'ROLE'::capability_source_type AS source_type
    FROM user_branch_assignment uba
    JOIN app_user au ON au.id = uba.user_id
    CROSS JOIN capability c
    WHERE au.status = 'ACTIVE'
      AND uba.ended_at IS NULL
      AND c.code IN ('VIEW_BRANCH_DATA', 'EDIT_BRANCH_DATA', 'VOID_SESSION', 'VIEW_COMMISSION')
      AND EXISTS (
          SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
          WHERE ur.user_id = uba.user_id AND r.name <> 'ONBOARDING'
      )

    UNION ALL

    -- (e) a SUPERUSER works at every branch: each non-management code of the role
    --     at every branch, with no assignment needed.
    SELECT
        ur.user_id,
        rc.capability_id,
        'BRANCH'::capability_context_type AS context_type,
        b.id AS context_id,
        5::smallint AS priority,
        'ROLE'::capability_source_type AS source_type
    FROM user_role ur
    JOIN app_user au ON au.id = ur.user_id
    JOIN role r ON r.id = ur.role_id
    JOIN role_capability rc ON rc.role_id = r.id
    JOIN capability c ON c.id = rc.capability_id
    CROSS JOIN branch b
    WHERE au.status = 'ACTIVE'
      AND r.name = 'SUPERUSER'
      AND c.code NOT IN ('MANAGE_USERS', 'ASSIGN_DELEGATE', 'MANAGE_CATALOG', 'ERASE_CLIENT')
) granted
ORDER BY granted.user_id, granted.capability_id, granted.context_type, granted.context_id, granted.priority DESC;

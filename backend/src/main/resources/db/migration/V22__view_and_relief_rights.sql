-- =============================================================================
-- V22__view_and_relief_rights.sql
--
-- User (owed #23, message 13065):
--   "relief has same access as branch members only for that granted day."
--   "Superuser can view and edit any branch without being a member."
--   Accountants and Owners view every branch (leg (b) VIEW_BRANCH_DATA); the
--   server's day reads now count that grant (authorization/Access.kt).
-- Adds two legs to active_user_capabilities; (a)-(d) are unchanged from V20:
--   (a2) an EDIT_BRANCH_DATA grant also grants VIEW_BRANCH_DATA in the same
--        context (who may edit may see), and a day grant (BRANCH_DAY, how relief
--        is granted) also grants VOID_SESSION and VIEW_COMMISSION for that day: a
--        member's rights.
--   (e)  a SUPERUSER holds every non-management code of the role at every
--        branch, as if a member of all of them.
--
-- User (owed #25, message 13112), leg (b):
--   pay at every branch (GLOBAL ASSIGN_COMPENSATION) is the Owner's and the
--   Superuser's; a Manager pays where they are a member (leg (c)), as before.
--   Practitioners manage products and categories as Coordinators, Managers and
--   Owners do (MANAGE_CATALOG joins the PRACTITIONER role below).
-- =============================================================================

INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r, capability c
WHERE r.name = 'PRACTITIONER' AND c.code = 'MANAGE_CATALOG'
ON CONFLICT DO NOTHING;

-- User (message 13114): "Commission isn't part of owner's pocket ... It's like their tip, it
-- should be private." Owed #26: "Only members of the same branch can see their commisions, since
-- they would be sharing it irl." VIEW_COMMISSION is seeing what members earned in commission.
-- Members hold it at their branch (leg (d)) and relief on its granted day (leg (a2)). No role
-- holds it everywhere: not the Owner, the Accountant, nor a Manager or Coordinator outside their
-- branch. The Superuser (the dev account) keeps it at every branch through leg (e).
INSERT INTO capability (code) VALUES ('VIEW_COMMISSION') ON CONFLICT DO NOTHING;

INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r, capability c
WHERE r.name = 'SUPERUSER' AND c.code = 'VIEW_COMMISSION'
ON CONFLICT DO NOTHING;

CREATE OR REPLACE VIEW active_user_capabilities AS
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
    --      VIEW_BRANCH_DATA in the same context, and on a day grant (relief)
    --      VOID_SESSION and VIEW_COMMISSION as well, so relief has a member's rights
    --      for its granted day. A GLOBAL edit grant implies nothing here.
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
        OR (implied.code IN ('VOID_SESSION', 'VIEW_COMMISSION') AND uc.context_type = 'BRANCH_DAY')
    WHERE au.status = 'ACTIVE'
      AND granted_code.code = 'EDIT_BRANCH_DATA'
      AND uc.context_type <> 'GLOBAL'
      AND now() BETWEEN uc.valid_from AND COALESCE(uc.valid_to, 'infinity'::timestamptz)

    UNION ALL

    -- (b) role-derived GLOBAL grants: management codes for
    --     SUPERUSER/OWNER/MANAGER, all-branches VIEW_BRANCH_DATA for
    --     SUPERUSER/OWNER/ACCOUNTANT, and catalog authority for
    --     SUPERUSER/OWNER/MANAGER/COORDINATOR (#436).
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
      AND c.code NOT IN ('MANAGE_USERS', 'ASSIGN_DELEGATE', 'MANAGE_CATALOG')

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
      AND c.code NOT IN ('MANAGE_USERS', 'ASSIGN_DELEGATE', 'MANAGE_CATALOG')
) granted
ORDER BY granted.user_id, granted.capability_id, granted.context_type, granted.context_id, granted.priority DESC;

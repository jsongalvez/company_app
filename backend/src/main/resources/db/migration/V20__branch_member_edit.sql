-- =============================================================================
-- V20__branch_member_edit.sql
--
-- User (owed #20): "Give everyone belonging to a branch (except temporary relief)
-- the same edit access." Adds leg (d) to active_user_capabilities: an active
-- branch assignment grants VIEW_BRANCH_DATA, EDIT_BRANCH_DATA and VOID_SESSION at
-- that branch, whatever the role (ONBOARDING alone excepted). Legs (a)-(c) are
-- unchanged from V1.
-- =============================================================================

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
              c.code IN ('MANAGE_USERS', 'ASSIGN_DELEGATE', 'ASSIGN_COMPENSATION')
              AND r.name IN ('SUPERUSER', 'OWNER', 'MANAGER')
          )
          OR
          (
              c.code = 'VIEW_BRANCH_DATA'
              AND r.name IN ('SUPERUSER', 'OWNER', 'ACCOUNTANT')
          )
          OR
          (
              c.code = 'MANAGE_CATALOG'
              AND r.name IN ('SUPERUSER', 'OWNER', 'MANAGER', 'COORDINATOR')
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
      AND c.code IN ('VIEW_BRANCH_DATA', 'EDIT_BRANCH_DATA', 'VOID_SESSION')
      AND EXISTS (
          SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
          WHERE ur.user_id = uba.user_id AND r.name <> 'ONBOARDING'
      )
) granted
ORDER BY granted.user_id, granted.capability_id, granted.context_type, granted.context_id, granted.priority DESC;

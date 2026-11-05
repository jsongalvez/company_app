-- Every Owner and Manager works each medical mission and provincial tour as staff, with no assignment: leg (f).
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

    -- (c) role-derived BRANCH grants: every non-management code of each
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

    -- (d) branch membership: everyone with an ACTIVE assignment at a
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

    UNION ALL

    -- (f) every OWNER and MANAGER is staff at each medical mission and provincial
    --     tour with no assignment: their role's non-management codes plus a
    --     member's, as (c) and (d) give at an assigned branch.
    SELECT
        ur.user_id,
        c.id AS capability_id,
        'BRANCH'::capability_context_type AS context_type,
        b.id AS context_id,
        5::smallint AS priority,
        'ROLE'::capability_source_type AS source_type
    FROM user_role ur
    JOIN app_user au ON au.id = ur.user_id
    JOIN role r ON r.id = ur.role_id
    JOIN branch b ON b.branch_type <> 'CLINIC'
    JOIN capability c
        ON c.code IN ('VIEW_BRANCH_DATA', 'EDIT_BRANCH_DATA', 'VOID_SESSION', 'VIEW_COMMISSION')
        OR EXISTS (SELECT 1 FROM role_capability rc WHERE rc.role_id = r.id AND rc.capability_id = c.id)
    WHERE au.status = 'ACTIVE'
      AND r.name IN ('OWNER', 'MANAGER')
      AND c.code NOT IN ('MANAGE_USERS', 'ASSIGN_DELEGATE', 'MANAGE_CATALOG', 'ERASE_CLIENT')
) granted
ORDER BY granted.user_id, granted.capability_id, granted.context_type, granted.context_id, granted.priority DESC;
